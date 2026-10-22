package dev.sh3lby.craftsocial.utils;

import dev.sh3lby.craftsocial.api.ApiException;
import dev.sh3lby.craftsocial.config.PluginConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Обработчик ошибок и состояний недоступности CRM.
 * Реализует read-only режим при недоступности CRM согласно MAX.md.
 */
public class ErrorHandler {
    private static final Logger logger = LoggerFactory.getLogger(ErrorHandler.class);
    
    private final PluginConfig config;
    private final AtomicBoolean crmAvailable = new AtomicBoolean(true);
    private final AtomicBoolean readOnlyMode = new AtomicBoolean(false);
    private final AtomicBoolean economyDisabled = new AtomicBoolean(false);
    private final AtomicBoolean socialDisabled = new AtomicBoolean(false);
    
    private final AtomicInteger consecutiveErrors = new AtomicInteger(0);
    private final AtomicLong lastErrorTime = new AtomicLong(0);
    private static final int MAX_CONSECUTIVE_ERRORS = 3;
    private static final long ERROR_RESET_TIME = 60000; // 1 минута
    
    public ErrorHandler(PluginConfig config) {
        this.config = config;
    }
    
    /**
     * Обрабатывает ошибку API.
     * Определяет, нужно ли переходить в read-only режим.
     */
    public void handleApiError(ApiException error) {
        long currentTime = System.currentTimeMillis();
        
        // Сбрасываем счетчик ошибок, если прошло достаточно времени
        if (currentTime - lastErrorTime.get() > ERROR_RESET_TIME) {
            consecutiveErrors.set(0);
        }
        
        lastErrorTime.set(currentTime);
        
        if (error.isServerError() || error.getStatusCode() == 0) {
            // Server error или network error
            int errors = consecutiveErrors.incrementAndGet();
            
            if (errors >= MAX_CONSECUTIVE_ERRORS) {
                logger.warn("CRM unavailable after {} consecutive errors, entering read-only mode", errors);
                setCrmUnavailable();
            }
        } else if (error.isClientError()) {
            // Client error (4xx) - не переходим в read-only режим
            logger.debug("Client error (not switching to read-only): {}", error.getError());
        }
        
        if (config.isLogAllErrors()) {
            logger.error("API Error: {} (Status: {})", error.getError(), error.getStatusCode(), error);
        }
    }
    
    /**
     * Обрабатывает успешный запрос - сбрасывает счетчик ошибок.
     */
    public void handleSuccess() {
        if (consecutiveErrors.get() > 0) {
            consecutiveErrors.set(0);
            if (!crmAvailable.get()) {
                logger.info("CRM available again, exiting read-only mode");
                setCrmAvailable();
            }
        }
    }
    
    /**
     * Устанавливает CRM как недоступную и активирует read-only режим.
     */
    private void setCrmUnavailable() {
        crmAvailable.set(false);
        
        if (config.isReadOnlyOnFailure()) {
            readOnlyMode.set(true);
            logger.warn("Read-only mode enabled - only cache reads allowed");
        }
        
        if (config.isDisableEconomyOnFailure()) {
            economyDisabled.set(true);
            logger.warn("Economy operations disabled due to CRM unavailability");
        }
        
        if (config.isDisableSocialOnFailure()) {
            socialDisabled.set(true);
            logger.warn("Social operations disabled due to CRM unavailability");
        }
    }
    
    /**
     * Устанавливает CRM как доступную и отключает read-only режим.
     */
    private void setCrmAvailable() {
        crmAvailable.set(true);
        readOnlyMode.set(false);
        economyDisabled.set(false);
        socialDisabled.set(false);
    }
    
    /**
     * Проверяет, доступна ли CRM.
     */
    public boolean isCrmAvailable() {
        return crmAvailable.get();
    }
    
    /**
     * Проверяет, включен ли read-only режим.
     */
    public boolean isReadOnlyMode() {
        return readOnlyMode.get();
    }
    
    /**
     * Проверяет, отключена ли экономика.
     */
    public boolean isEconomyDisabled() {
        return economyDisabled.get();
    }
    
    /**
     * Проверяет, отключены ли социальные функции.
     */
    public boolean isSocialDisabled() {
        return socialDisabled.get();
    }
    
    /**
     * Проверяет, можно ли выполнить операцию записи.
     * 
     * @param operationType Тип операции (economy, social, etc.)
     * @return true если операция разрешена
     */
    public boolean canPerformOperation(String operationType) {
        if (!isCrmAvailable()) {
            return false;
        }
        
        if (isReadOnlyMode()) {
            return false;
        }
        
        if ("economy".equals(operationType) && isEconomyDisabled()) {
            return false;
        }
        
        if ("social".equals(operationType) && isSocialDisabled()) {
            return false;
        }
        
        return true;
    }
    
    /**
     * Получает сообщение об ошибке для игрока.
     */
    public String getErrorMessage(String operationType) {
        if (!isCrmAvailable()) {
            return "Система временно недоступна. Попробуйте позже.";
        }
        
        if (isReadOnlyMode()) {
            return "Система в режиме только чтения. Операции записи недоступны.";
        }
        
        if ("economy".equals(operationType) && isEconomyDisabled()) {
            return "Экономические операции временно отключены.";
        }
        
        if ("social".equals(operationType) && isSocialDisabled()) {
            return "Социальные функции временно отключены.";
        }
        
        return "Операция недоступна.";
    }
}
