package dev.sh3lby.craftsocial.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import dev.sh3lby.craftsocial.config.PluginConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * Менеджер кеша с TTL.
 * Read-only кеш для быстрого доступа к данным без блокировки главного потока.
 */
public class CacheManager {
    private static final Logger logger = LoggerFactory.getLogger(CacheManager.class);
    
    private final Cache<String, CacheEntry<?>> cache;
    private final boolean enabled;
    private final long ttlSeconds;
    private final PluginConfig config;
    
    public CacheManager(PluginConfig config) {
        this.config = config;
        this.enabled = config.isCacheEnabled();
        this.ttlSeconds = config.getCacheTtl();
        
        if (enabled) {
            this.cache = Caffeine.newBuilder()
                .maximumSize(config.getCacheMaxSize())
                .expireAfterWrite(Duration.ofSeconds(ttlSeconds))
                .build();
            
            logger.info("CacheManager initialized with TTL {} seconds and max size {}", ttlSeconds, config.getCacheMaxSize());
        } else {
            this.cache = null;
            logger.info("CacheManager disabled");
        }
    }
    
    /**
     * Получает значение из кеша или вычисляет его асинхронно, если кеш отключен или значение отсутствует.
     * 
     * @param key Ключ кеша
     * @param supplier Поставщик данных (выполняется асинхронно)
     * @param clazz Класс данных
     * @return CompletableFuture с данными
     */
    @SuppressWarnings("unchecked")
    public <T> CompletableFuture<T> getOrCompute(String key, Supplier<CompletableFuture<T>> supplier, Class<T> clazz) {
        if (!enabled || cache == null) {
            // Кеш отключен - выполняем supplier напрямую
            return supplier.get();
        }
        
        // Проверяем кеш синхронно (быстро, не блокирует)
        CacheEntry<?> entry = cache.getIfPresent(key);
        if (entry != null && !entry.isExpired()) {
            // Кеш валиден - возвращаем данные
            if (config.isDebug()) {
                logger.debug("Cache hit for key: {}", key);
            }
            return CompletableFuture.completedFuture((T) entry.getData());
        }
        
        // Кеш отсутствует или устарел - вычисляем асинхронно
        if (config.isDebug()) {
            logger.debug("Cache miss for key: {}, computing...", key);
        }
        
        return supplier.get().thenApply(data -> {
            // Сохраняем в кеш после получения данных
            if (data != null) {
                cache.put(key, new CacheEntry<>(data, ttlSeconds));
            }
            return data;
        });
    }
    
    /**
     * Получает значение из кеша синхронно (только если кеш валиден).
     * 
     * @param key Ключ кеша
     * @param clazz Класс данных
     * @return Данные или null если кеш отсутствует/устарел
     */
    @SuppressWarnings("unchecked")
    public <T> T get(String key, Class<T> clazz) {
        if (!enabled || cache == null) {
            return null;
        }
        
        CacheEntry<?> entry = cache.getIfPresent(key);
        if (entry != null && !entry.isExpired()) {
            return (T) entry.getData();
        }
        
        return null;
    }
    
    /**
     * Сохраняет значение в кеш.
     * 
     * @param key Ключ кеша
     * @param data Данные для кеширования
     */
    public <T> void put(String key, T data) {
        if (!enabled || cache == null) {
            return;
        }
        
        cache.put(key, new CacheEntry<>(data, ttlSeconds));
    }
    
    /**
     * Инвалидирует кеш по ключу.
     * 
     * @param key Ключ кеша
     */
    public void invalidate(String key) {
        if (cache != null) {
            cache.invalidate(key);
            if (config.isDebug()) {
                logger.debug("Cache invalidated for key: {}", key);
            }
        }
    }
    
    /**
     * Очищает весь кеш.
     */
    public void clear() {
        if (cache != null) {
            cache.invalidateAll();
            logger.info("Cache cleared");
        }
    }
    
    /**
     * Получает статистику кеша.
     * 
     * @return Статистика кеша
     */
    public String getStats() {
        if (cache == null) {
            return "Cache disabled";
        }
        
        return String.format("Cache stats: size=%d, hitRate=%.2f%%", 
            cache.estimatedSize(),
            cache.stats().hitRate() * 100);
    }
}
