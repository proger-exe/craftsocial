package dev.sh3lby.craftsocial.handlers;

import dev.sh3lby.craftsocial.api.ApiClient;
import dev.sh3lby.craftsocial.cache.CacheManager;
import dev.sh3lby.craftsocial.models.Party;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Обработчик групп (Party).
 * Работает с группами через /user/parties.
 */
public class PartyHandler {
    private static final Logger logger = LoggerFactory.getLogger(PartyHandler.class);
    
    private final ApiClient apiClient;
    private final CacheManager cacheManager;
    
    public PartyHandler(ApiClient apiClient, CacheManager cacheManager) {
        this.apiClient = apiClient;
        this.cacheManager = cacheManager;
    }
    
    /**
     * Получает группу по ID (с кешированием).
     */
    public CompletableFuture<Party> getParty(int partyId, long playerId) {
        String cacheKey = "party:" + partyId;
        
        return cacheManager.getOrCompute(cacheKey, () -> {
            return apiClient.get("/user/parties/" + partyId, Party.class, playerId)
                .thenApply(response -> {
                    if (response.isSuccess() && response.getData() != null) {
                        return response.getData();
                    } else {
                        logger.warn("Failed to get party {}: {}", partyId, response.getError());
                        return null;
                    }
                });
        }, Party.class);
    }
    
    /**
     * Создает группу.
     */
    public CompletableFuture<Party> createParty(String name, long playerId) {
        Map<String, Object> request = new HashMap<>();
        request.put("name", name);
        
        return apiClient.post("/user/parties", request, Party.class, playerId)
            .thenApply(response -> {
                if (response.isSuccess() && response.getData() != null) {
                    return response.getData();
                } else {
                    logger.warn("Failed to create party: {}", response.getError());
                    return null;
                }
            });
    }
    
    /**
     * Добавляет участника в группу (эквивалент /party/join).
     * API возвращает PartyResponse, а не PartyMember.
     */
    public CompletableFuture<Boolean> addMember(int partyId, long newPlayerId, long playerId) {
        Map<String, Object> request = new HashMap<>();
        request.put("player_id", newPlayerId);
        
        return apiClient.post("/user/parties/" + partyId + "/members", request, Party.class, playerId)
            .thenApply(response -> {
                if (response.isSuccess()) {
                    // Инвалидируем кеш группы
                    cacheManager.invalidate("party:" + partyId);
                    return true;
                } else {
                    logger.warn("Failed to add member to party {}: {}", partyId, response.getError());
                    return false;
                }
            });
    }
    
    /**
     * Удаляет участника из группы.
     */
    public CompletableFuture<Boolean> removeMember(int partyId, long memberId, long playerId) {
        return apiClient.delete("/user/parties/" + partyId + "/members/" + memberId, Void.class, playerId)
            .thenApply(response -> {
                if (response.isSuccess()) {
                    // Инвалидируем кеш группы
                    cacheManager.invalidate("party:" + partyId);
                    return true;
                } else {
                    logger.warn("Failed to remove member from party {}: {}", partyId, response.getError());
                    return false;
                }
            });
    }
    
    /**
     * Передает лидерство в группе.
     */
    public CompletableFuture<Boolean> transferLeadership(int partyId, long newLeaderId, long playerId) {
        String endpoint = "/user/parties/" + partyId + "/transfer-leadership?new_leader_id=" + newLeaderId;
        return apiClient.post(endpoint, null, Void.class, playerId)
            .thenApply(response -> {
                if (response.isSuccess()) {
                    // Инвалидируем кеш группы
                    cacheManager.invalidate("party:" + partyId);
                    return true;
                } else {
                    logger.warn("Failed to transfer leadership in party {}: {}", partyId, response.getError());
                    return false;
                }
            });
    }
    
    /**
     * Обновляет группу.
     */
    public CompletableFuture<Boolean> updateParty(int partyId, String name, long playerId) {
        Map<String, Object> request = new HashMap<>();
        request.put("name", name);
        
        return apiClient.put("/user/parties/" + partyId, request, Party.class, playerId)
            .thenApply(response -> {
                if (response.isSuccess()) {
                    // Инвалидируем кеш группы
                    cacheManager.invalidate("party:" + partyId);
                    return true;
                } else {
                    logger.warn("Failed to update party {}: {}", partyId, response.getError());
                    return false;
                }
            });
    }
    
    /**
     * Удаляет группу.
     */
    public CompletableFuture<Boolean> deleteParty(int partyId, long playerId) {
        return apiClient.delete("/user/parties/" + partyId, Void.class, playerId)
            .thenApply(response -> {
                if (response.isSuccess()) {
                    // Инвалидируем кеш группы
                    cacheManager.invalidate("party:" + partyId);
                    return true;
                } else {
                    logger.warn("Failed to delete party {}: {}", partyId, response.getError());
                    return false;
                }
            });
    }
    
    /**
     * Получает список групп. Не кэшируем: пати меняются на каждый create/join/leave/delete,
     * а инвалидация плагином тут дырявая (без владельца пати не понять, какой ключ бить).
     */
    public CompletableFuture<java.util.List<Party>> getParties(int page, int perPage, long playerId) {
        String endpoint = "/user/parties?page=" + page + "&per_page=" + perPage;
        return apiClient.get(endpoint, java.util.List.class, playerId)
            .thenApply(response -> {
                if (response.isSuccess() && response.getRawJson() != null) {
                    return dev.sh3lby.craftsocial.utils.GsonUtils.deserializeList(
                        response.getRawJson(),
                        Party.class);
                }
                logger.warn("Failed to get parties: {}", response.getError());
                return null;
            });
    }
}
