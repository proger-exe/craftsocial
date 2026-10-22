package dev.sh3lby.craftsocial.utils;

import dev.sh3lby.craftsocial.api.ApiClient;
import dev.sh3lby.craftsocial.cache.CacheManager;
import org.bukkit.entity.Player;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Маппер для преобразования UUID игрока в player_id из CRM.
 * Кеширует маппинг для быстрого доступа.
 */
public class PlayerIdMapper {
    private static final Logger logger = LoggerFactory.getLogger(PlayerIdMapper.class);
    
    private final ApiClient apiClient;
    private final CacheManager cacheManager;
    private final ConcurrentHashMap<UUID, Long> uuidToPlayerIdCache = new ConcurrentHashMap<>();
    
    public PlayerIdMapper(ApiClient apiClient, CacheManager cacheManager) {
        this.apiClient = apiClient;
        this.cacheManager = cacheManager;
    }
    
    /**
     * Получает player_id для игрока (с кешированием).
     * Если player_id не найден в профиле, использует хеш UUID как временное решение.
     */
    public CompletableFuture<Long> getPlayerId(Player player) {
        UUID uuid = player.getUniqueId();
        
        // Проверяем кеш
        Long cachedId = uuidToPlayerIdCache.get(uuid);
        if (cachedId != null) {
            return CompletableFuture.completedFuture(cachedId);
        }
        
        // Проверяем кеш плагина
        String cacheKey = "player_id:" + uuid.toString();
        Long cachedPlayerId = cacheManager.get(cacheKey, Long.class);
        if (cachedPlayerId != null) {
            uuidToPlayerIdCache.put(uuid, cachedPlayerId);
            return CompletableFuture.completedFuture(cachedPlayerId);
        }
        
        // Используем временный ID на основе хеша UUID
        // В будущем можно добавить endpoint для получения player_id по UUID
        long tempId = Integer.toUnsignedLong(uuid.hashCode());
        uuidToPlayerIdCache.put(uuid, tempId);
        cacheManager.put(cacheKey, tempId);
        return CompletableFuture.completedFuture(tempId);
    }
    
    /**
     * Получает player_id синхронно (из кеша или временный).
     * Используется когда нужен быстрый доступ без ожидания API.
     */
    public long getPlayerIdSync(Player player) {
        UUID uuid = player.getUniqueId();
        
        // Проверяем кеш
        Long cachedId = uuidToPlayerIdCache.get(uuid);
        if (cachedId != null) {
            return cachedId;
        }
        
        // Проверяем кеш плагина
        String cacheKey = "player_id:" + uuid.toString();
        Long cachedPlayerId = cacheManager.get(cacheKey, Long.class);
        if (cachedPlayerId != null) {
            uuidToPlayerIdCache.put(uuid, cachedPlayerId);
            return cachedPlayerId;
        }
        
        // Используем временный ID на основе хеша UUID
        long tempId = Integer.toUnsignedLong(uuid.hashCode());
        uuidToPlayerIdCache.put(uuid, tempId);
        cacheManager.put(cacheKey, tempId);
        return tempId;
    }
    
    /**
     * Сохраняет маппинг UUID -> player_id.
     */
    public void setPlayerId(UUID uuid, long playerId) {
        uuidToPlayerIdCache.put(uuid, playerId);
        String cacheKey = "player_id:" + uuid.toString();
        cacheManager.put(cacheKey, playerId);
    }
    
    /**
     * Очищает кеш маппинга.
     */
    public void clearCache() {
        uuidToPlayerIdCache.clear();
    }
}
