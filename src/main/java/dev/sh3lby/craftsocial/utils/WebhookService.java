package dev.sh3lby.craftsocial.utils;

import com.google.gson.JsonObject;
import dev.sh3lby.craftsocial.api.ApiClient;
import dev.sh3lby.craftsocial.api.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Сервис для отправки событий в CRM через внутренний API.
 * Сейчас умеет ставить в очередь награды за игровое время (/admin/playtime-rewards).
 */
public class WebhookService {
    private static final Logger logger = LoggerFactory.getLogger(WebhookService.class);
    /** ID системного игрока-владельца для admin-эндпоинтов. ApiClient автоматически
     *  прикрепит Authorization: Bearer для этого id, если сконфигурирован service token. */
    private static final long ADMIN_PLAYER_ID = ApiClient.SYSTEM_PLAYER_ID;

    private final ApiClient apiClient;

    public WebhookService(ApiClient apiClient) {
        this.apiClient = apiClient;
    }

    /**
     * Создает записи в очереди наград за игровое время для одного игрока.
     * endpoint: POST /admin/playtime-rewards
     *
     * Тело:
     * {
     *   "players": [
     *     { "player_id": 123, "playtime_seconds": 7200 }
     *   ]
     * }
     */
    public void pushPlaytimeReward(long playerId, int playtimeSeconds) {
        if (playerId <= 0 || playtimeSeconds <= 0) {
            return;
        }
        logger.info("Pushing playtime reward to bot: player_id={}, playtime_seconds={}", playerId, playtimeSeconds);
        Map<String, Object> playerEntry = new HashMap<>();
        playerEntry.put("player_id", playerId);
        playerEntry.put("playtime_seconds", playtimeSeconds);

        Map<String, Object> payload = new HashMap<>();
        payload.put("players", List.of(playerEntry));

        apiClient.post("/admin/playtime-rewards", payload, Void.class, ADMIN_PLAYER_ID)
            .thenAccept(response -> logPlaytimeResponse(playerId, playtimeSeconds, response))
            .exceptionally(error -> {
                logger.warn("Failed to push playtime rewards for player_id={} ({}s): {}",
                    playerId, playtimeSeconds, error.getMessage(), error);
                return null;
            });
    }

    /**
     * Отправляет боту пакет игроков, получивших награду за онлайн (один запрос на всех).
     * Каждый элемент списка — Map с ключами "player_id" (Long) и "playtime_seconds" (Integer).
     */
    public void pushPlaytimeRewardBatch(List<Map<String, Object>> playerEntries) {
        if (playerEntries == null || playerEntries.isEmpty()) {
            return;
        }
        List<Map<String, Object>> valid = playerEntries.stream()
            .filter(e -> {
                Object id = e.get("player_id");
                Object sec = e.get("playtime_seconds");
                if (id instanceof Number && sec instanceof Number) {
                    long pid = ((Number) id).longValue();
                    int s = ((Number) sec).intValue();
                    return pid > 0 && s > 0;
                }
                return false;
            })
            .collect(Collectors.toList());
        if (valid.isEmpty()) {
            return;
        }
        logger.info("Pushing playtime rewards batch to bot: {} players", valid.size());
        Map<String, Object> payload = new HashMap<>();
        payload.put("players", valid);
        apiClient.post("/admin/playtime-rewards", payload, Void.class, ADMIN_PLAYER_ID)
            .thenAccept(response -> logPlaytimeBatchResponse(valid.size(), response))
            .exceptionally(error -> {
                logger.warn("Failed to push playtime rewards batch ({} players): {}",
                    valid.size(), error.getMessage(), error);
                return null;
            });
    }

    private void logPlaytimeBatchResponse(int playerCount, ApiResponse<Void> response) {
        if (response == null || !response.isSuccess()) {
            logger.warn("Playtime rewards batch rejected: HTTP {} | {} | {}",
                response != null ? response.getStatusCode() : 0,
                response != null ? response.getError() : "no response",
                response != null ? response.getDetail() : "");
            return;
        }
        JsonObject raw = response != null ? response.getRawJson() : null;
        if (raw != null && raw.has("inserted")) {
            try {
                int inserted = raw.get("inserted").getAsInt();
                logger.info("Playtime rewards batch queued for bot: {} players, inserted={}",
                    playerCount, inserted);
            } catch (Exception e) {
                logger.warn("Playtime rewards batch response has non-int inserted: {}", raw.toString());
            }
        } else {
            logger.info("Playtime rewards batch ({} players) sent to bot", playerCount);
        }
    }

    private void logPlaytimeResponse(long playerId, int playtimeSeconds, ApiResponse<Void> response) {
        if (response == null || !response.isSuccess()) {
            logger.warn("Playtime reward rejected for player_id={}: HTTP {} | {} | {}",
                playerId,
                response != null ? response.getStatusCode() : 0,
                response != null ? response.getError() : "no response",
                response != null ? response.getDetail() : "");
            return;
        }
        JsonObject raw = response != null ? response.getRawJson() : null;
        if (raw != null && raw.has("inserted")) {
            int inserted = 0;
            try {
                inserted = raw.get("inserted").getAsInt();
            } catch (Exception e) {
                logger.warn("Playtime rewards response has non-int inserted field for player_id={}: {}",
                    playerId, raw.toString());
            }
            logger.info("Playtime rewards queued via /admin/playtime-rewards: inserted={} for player_id={} ({}s)",
                inserted, playerId, playtimeSeconds);
        } else {
            logger.info("Playtime rewards call for player_id={} ({}s) completed without 'inserted' field in response",
                playerId, playtimeSeconds);
        }
    }
}
