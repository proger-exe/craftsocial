package dev.sh3lby.craftsocial.handlers;

import dev.sh3lby.craftsocial.api.ApiClient;
import dev.sh3lby.craftsocial.cache.CacheManager;
import dev.sh3lby.craftsocial.models.PlayerAnalytics;
import dev.sh3lby.craftsocial.models.PlayerProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;

/**
 * Обработчик действий игрока.
 * Работает с профилем и аналитикой через API.
 */
public class PlayerHandler {
    private static final Logger logger = LoggerFactory.getLogger(PlayerHandler.class);
    
    private final ApiClient apiClient;
    private final CacheManager cacheManager;
    
    public PlayerHandler(ApiClient apiClient, CacheManager cacheManager) {
        this.apiClient = apiClient;
        this.cacheManager = cacheManager;
    }
    
    /**
     * Получает профиль игрока (с кешированием).
     */
    public CompletableFuture<PlayerProfile> getProfile(long playerId) {
        String cacheKey = "profile:" + playerId;
        
        return cacheManager.getOrCompute(cacheKey, () -> {
            return apiClient.get("/user/profile", PlayerProfile.class, playerId)
                .thenApply(response -> {
                    if (response.isSuccess() && response.getData() != null) {
                        return response.getData();
                    } else {
                        logger.warn("Failed to get profile for player {}: {}", playerId, response.getError());
                        return null;
                    }
                });
        }, PlayerProfile.class);
    }
    
    /**
     * Получает аналитику игрока (с кешированием).
     */
    public CompletableFuture<PlayerAnalytics> getAnalytics(long playerId) {
        String cacheKey = "analytics:" + playerId;
        
        return cacheManager.getOrCompute(cacheKey, () -> {
            return apiClient.get("/user/analytics", PlayerAnalytics.class, playerId)
                .thenApply(response -> {
                    if (response.isSuccess() && response.getData() != null) {
                        return response.getData();
                    } else {
                        logger.warn("Failed to get analytics for player {}: {}", playerId, response.getError());
                        return null;
                    }
                });
        }, PlayerAnalytics.class);
    }
    
    /**
     * Инвалидирует кеш аналитики игрока (после вступления/выхода из клана или группы, кика).
     */
    public void invalidateAnalytics(long playerId) {
        cacheManager.invalidate("analytics:" + playerId);
    }
    
    /**
     * Обновляет профиль игрока.
     */
    public CompletableFuture<Boolean> updateProfile(long playerId, PlayerProfile profile) {
        return apiClient.put("/user/profile", profile, PlayerProfile.class, playerId)
            .thenApply(response -> {
                if (response.isSuccess()) {
                    // Инвалидируем кеш
                    cacheManager.invalidate("profile:" + playerId);
                    return true;
                } else {
                    logger.warn("Failed to update profile for player {}: {}", playerId, response.getError());
                    return false;
                }
            });
    }
    
    /**
     * Обновляет аналитику игрока.
     */
    public CompletableFuture<Boolean> updateAnalytics(long playerId, PlayerAnalytics analytics) {
        return apiClient.put("/user/analytics", analytics, PlayerAnalytics.class, playerId)
            .thenApply(response -> {
                if (response.isSuccess()) {
                    // Инвалидируем кеш
                    cacheManager.invalidate("analytics:" + playerId);
                    return true;
                } else {
                    logger.warn("Failed to update analytics for player {}: {}", playerId, response.getError());
                    return false;
                }
            });
    }
    
    /**
     * Создает профиль игрока.
     * POST /user/profile без заголовка X-Player-ID.
     *
     * Формат ответа API (объект в корне, без обёртки success/data):
     * {"id": int, "player_id": long, "nickname": string, "age": int?, "gender": string?, "source": string?}
     * player_id — 64-bit, обязательно берётся из ответа для маппинга UUID→player_id.
     *
     * Тело запроса: nickname (required), uuid (optional), source (optional).
     */
    public CompletableFuture<PlayerProfile> createProfile(String nickname, String uuid) {
        java.util.Map<String, Object> request = new java.util.HashMap<>();
        // API требует player_id в теле запроса, отправляем случайное число
        // API вернет реальный player_id в ответе
        request.put("player_id", new java.util.Random().nextInt(Integer.MAX_VALUE));
        request.put("nickname", nickname);
        request.put("source", "api");
        if (uuid != null) {
            request.put("uuid", uuid);
        }
        
        // Используем метод без player_id в заголовке, так как для создания профиля он не требуется
        return apiClient.postWithoutPlayerId("/user/profile", request, PlayerProfile.class)
            .thenApply(response -> {
                if (response.isSuccess() && response.getData() != null) {
                    PlayerProfile profile = response.getData();
                    long createdPlayerId = profile.getPlayerId();
                    // Инвалидируем кеш
                    if (createdPlayerId > 0) {
                        cacheManager.invalidate("profile:" + createdPlayerId);
                    }
                    logger.info("Profile created for player {} (nickname: {}, player_id: {})", 
                        nickname, nickname, createdPlayerId);
                    return profile;
                } else {
                    logger.warn("Failed to create profile for player {}: {} (status: {})", 
                        nickname, response.getError(), response.getStatusCode());
                    return null;
                }
            });
    }
    
    /**
     * Проверяет существование профиля игрока.
     * Возвращает true если профиль существует, false если нет (404).
     */
    public CompletableFuture<Boolean> checkProfileExists(long playerId) {
        return apiClient.get("/user/profile", PlayerProfile.class, playerId)
            .thenApply(response -> {
                if (response.isSuccess() && response.getData() != null) {
                    return true;
                } else if (response.getStatusCode() == 404) {
                    return false;
                } else {
                    logger.warn("Error checking profile for player {}: {} (status: {})", 
                        playerId, response.getError(), response.getStatusCode());
                    return false;
                }
            });
    }
    
}
