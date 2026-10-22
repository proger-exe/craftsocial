package dev.sh3lby.craftsocial.handlers;

import dev.sh3lby.craftsocial.api.ApiClient;
import dev.sh3lby.craftsocial.cache.CacheManager;
import dev.sh3lby.craftsocial.models.Clan;
import dev.sh3lby.craftsocial.models.ClanMember;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Обработчик кланов.
 * Работает с кланами через /user/clans.
 */
public class ClanHandler {
    private static final Logger logger = LoggerFactory.getLogger(ClanHandler.class);
    public static final int MAX_CLAN_HOMES = 3;
    
    private final ApiClient apiClient;
    private final CacheManager cacheManager;
    
    public ClanHandler(ApiClient apiClient, CacheManager cacheManager) {
        this.apiClient = apiClient;
        this.cacheManager = cacheManager;
    }
    
    /**
     * Инвалидирует кеш клана (после обновления homes/inventory и перед повторным открытием GUI).
     */
    public void invalidateClanCache(int clanId) {
        cacheManager.invalidate("clan:" + clanId);
    }
    
    /**
     * Получает клан по ID (с кешированием).
     * API возвращает members как массив пар [player_id, role]; список участников заполняется лениво в Clan.getMembers().
     */
    public CompletableFuture<Clan> getClan(int clanId, long playerId) {
        String cacheKey = "clan:" + clanId;
        
        return cacheManager.getOrCompute(cacheKey, () -> {
            return apiClient.get("/user/clans/" + clanId, Clan.class, playerId)
                .thenApply(response -> {
                    if (response.isSuccess() && response.getData() != null) {
                        return response.getData();
                    } else {
                        logger.warn("Failed to get clan {}: {}", clanId, response.getError());
                        return null;
                    }
                });
        }, Clan.class);
    }
    
    /**
     * Создает клан.
     */
    public CompletableFuture<Clan> createClan(String name, String tag, String description, String prefix, long playerId) {
        Map<String, Object> request = new HashMap<>();
        request.put("name", name);
        request.put("tag", tag);
        request.put("description", description);
        request.put("prefix", prefix);
        
        return apiClient.post("/user/clans", request, Clan.class, playerId)
            .thenApply(response -> {
                if (response.isSuccess() && response.getData() != null) {
                    return response.getData();
                } else {
                    logger.warn("Failed to create clan: {}", response.getError());
                    return null;
                }
            });
    }
    
    /**
     * Добавляет участника в клан.
     */
    public CompletableFuture<Boolean> addMember(int clanId, long newPlayerId, String role, long playerId) {
        Map<String, Object> request = new HashMap<>();
        request.put("player_id", newPlayerId);
        request.put("role", role);
        
        return apiClient.post("/user/clans/" + clanId + "/members", request, ClanMember.class, playerId)
            .thenApply(response -> {
                if (response.isSuccess()) {
                    // Инвалидируем кеш клана
                    cacheManager.invalidate("clan:" + clanId);
                    return true;
                } else {
                    logger.warn("Failed to add member to clan {}: {}", clanId, response.getError());
                    return false;
                }
            });
    }
    
    /**
     * Удаляет участника из клана.
     * memberId - это player_id участника, а не ID записи ClanMember.
     */
    public CompletableFuture<Boolean> removeMember(int clanId, long memberId, long playerId) {
        // member_id в API - это player_id участника
        return apiClient.delete("/user/clans/" + clanId + "/members/" + memberId, Void.class, playerId)
            .thenApply(response -> {
                if (response.isSuccess()) {
                    // Инвалидируем кеш клана
                    cacheManager.invalidate("clan:" + clanId);
                    return true;
                } else {
                    logger.warn("Failed to remove member from clan {}: {}", clanId, response.getError());
                    return false;
                }
            });
    }
    
    /**
     * Изменяет роль участника.
     */
    public CompletableFuture<Boolean> changeMemberRole(int clanId, long memberId, String newRole, long playerId) {
        Map<String, Object> request = new HashMap<>();
        request.put("role", newRole);
        
        return apiClient.put("/user/clans/" + clanId + "/members/" + memberId + "/role", request, Void.class, playerId)
            .thenApply(response -> {
                if (response.isSuccess()) {
                    // Инвалидируем кеш клана
                    cacheManager.invalidate("clan:" + clanId);
                    return true;
                } else {
                    logger.warn("Failed to change member role in clan {}: {}", clanId, response.getError());
                    return false;
                }
            });
    }
    
    /**
     * Обновляет клан (name, tag, description, prefix). Опционально homes и inventory.
     * При успехе возвращает обновлённый клан из ответа PUT (кеш инвалидируется и заполняется им).
     */
    public CompletableFuture<Clan> updateClan(int clanId, String name, String tag, String description, String prefix,
                                                  List<Map<String, Object>> homes, List<Object> inventory, long playerId) {
        Map<String, Object> request = new HashMap<>();
        if (name != null) request.put("name", name);
        if (tag != null) request.put("tag", tag);
        if (description != null) request.put("description", description);
        if (prefix != null) request.put("prefix", prefix);
        if (homes != null) request.put("homes", homes);
        if (inventory != null) request.put("inventory", inventory);
        
        return apiClient.put("/user/clans/" + clanId, request, Clan.class, playerId)
            .thenApply(response -> {
                if (response.isSuccess() && response.getData() != null) {
                    cacheManager.invalidate("clan:" + clanId);
                    cacheManager.put("clan:" + clanId, response.getData());
                    return response.getData();
                } else {
                    logger.warn("Failed to update clan {}: {}", clanId, response.getError());
                    return (Clan) null;
                }
            });
    }
    
    /**
     * Обновляет только базовые поля клана (без homes/inventory).
     */
    public CompletableFuture<Clan> updateClan(int clanId, String name, String tag, String description, String prefix, long playerId) {
        return updateClan(clanId, name, tag, description, prefix, null, null, playerId);
    }
    
    /**
     * Обновляет инвентарь клана (список объектов для API).
     * Возвращает обновлённый клан из ответа PUT или null при ошибке.
     */
    public CompletableFuture<Clan> updateClanInventory(int clanId, List<Object> inventory, long playerId) {
        return updateClan(clanId, null, null, null, null, null, inventory, playerId);
    }
    
    /**
     * Добавляет дом клана (макс. MAX_CLAN_HOMES). name, dim, x, y, z.
     * Возвращает обновлённый клан из ответа PUT или null при ошибке.
     */
    public CompletableFuture<Clan> addHome(int clanId, String name, String dim, double x, double y, double z, long playerId) {
        return getClan(clanId, playerId).thenCompose(clan -> {
            if (clan == null) return CompletableFuture.completedFuture((Clan) null);
            List<Map<String, Object>> homes = new ArrayList<>(clan.getHomes() != null ? clan.getHomes() : List.of());
            if (homes.size() >= MAX_CLAN_HOMES) return CompletableFuture.completedFuture((Clan) null);
            for (Map<String, Object> h : homes) {
                if (name.equals(h.get("name"))) return CompletableFuture.completedFuture((Clan) null);
            }
            Map<String, Object> home = new HashMap<>();
            home.put("name", name);
            home.put("dim", dim);
            home.put("x", x);
            home.put("y", y);
            home.put("z", z);
            homes.add(home);
            return updateClan(clanId, null, null, null, null, homes, null, playerId);
        });
    }
    
    /**
     * Удаляет дом клана по имени.
     * Возвращает обновлённый клан из ответа PUT или null при ошибке.
     */
    public CompletableFuture<Clan> removeHome(int clanId, String name, long playerId) {
        return getClan(clanId, playerId).thenCompose(clan -> {
            if (clan == null || clan.getHomes() == null) return CompletableFuture.completedFuture((Clan) null);
            List<Map<String, Object>> homes = new ArrayList<>(clan.getHomes());
            homes.removeIf(h -> name.equals(h.get("name")));
            return updateClan(clanId, null, null, null, null, homes, null, playerId);
        });
    }
    
    /**
     * Удаляет клан.
     */
    public CompletableFuture<Boolean> deleteClan(int clanId, long playerId) {
        return apiClient.delete("/user/clans/" + clanId, Void.class, playerId)
            .thenApply(response -> {
                if (response.isSuccess()) {
                    // Инвалидируем кеш клана
                    cacheManager.invalidate("clan:" + clanId);
                    return true;
                } else {
                    logger.warn("Failed to delete clan {}: {}", clanId, response.getError());
                    return false;
                }
            });
    }
    
    /**
     * Получает список кланов (с кешированием).
     */
    @SuppressWarnings("unchecked")
    public CompletableFuture<java.util.List<Clan>> getClans(String name, String tag, String search, int page, int perPage, long playerId) {
        String cacheKey = "clans:list:" + name + ":" + tag + ":" + search + ":" + page;
        
        return cacheManager.getOrCompute(cacheKey, () -> {
            StringBuilder endpoint = new StringBuilder("/user/clans?page=").append(page).append("&per_page=").append(perPage);
            if (name != null) endpoint.append("&name=").append(name);
            if (tag != null) endpoint.append("&tag=").append(tag);
            if (search != null) endpoint.append("&search=").append(search);
            
            return apiClient.get(endpoint.toString(), java.util.List.class, playerId)
                .thenApply(response -> {
                    if (response.isSuccess() && response.getData() != null) {
                        // Десериализация списка кланов
                        if (response.getRawJson() != null) {
                            return dev.sh3lby.craftsocial.utils.GsonUtils.deserializeList(
                                response.getRawJson(), 
                                Clan.class
                            );
                        }
                        return new java.util.ArrayList<>();
                    } else {
                        logger.warn("Failed to get clans: {}", response.getError());
                        return null;
                    }
                });
        }, java.util.List.class).thenApply(data -> (java.util.List<Clan>) data);
    }
    
    /**
     * Проверяет права игрока в клане (через получение данных клана и проверку роли).
     */
    public CompletableFuture<Boolean> hasPermission(int clanId, long playerId, String permission) {
        return getClan(clanId, playerId).thenApply(clan -> {
            if (clan == null || clan.getMembers() == null) {
                return false;
            }
            
            ClanMember member = clan.getMembers().stream()
                .filter(m -> m.getPlayerId() == playerId)
                .findFirst()
                .orElse(null);
            
            if (member == null) {
                return false;
            }
            
            // Простая проверка прав на основе роли
            // В реальной системе должна быть granular permissions через API
            switch (permission) {
                case "manage_members":
                case "manage_roles":
                case "withdraw_money":
                    return member.isModerator();
                case "delete_clan":
                    return member.isLeader();
                default:
                    return true; // Базовые права для всех участников
            }
        });
    }
}
