package dev.sh3lby.craftsocial.listeners;

import dev.sh3lby.craftsocial.CraftSocialPlugin;
import dev.sh3lby.craftsocial.handlers.PlayerHandler;
import dev.sh3lby.craftsocial.storage.PlayerActivityTracker;
import dev.sh3lby.craftsocial.utils.MessagePalette;
import dev.sh3lby.craftsocial.utils.PlayerIdMapper;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Listener для обработки входа игроков на сервер.
 * Проверяет существование профиля в CRM и создает его при необходимости.
 */
public class PlayerJoinListener implements Listener {
    private static final Logger logger = LoggerFactory.getLogger(PlayerJoinListener.class);
    
    private final Plugin plugin;
    private final PlayerHandler playerHandler;
    private final PlayerIdMapper playerIdMapper;
    private final PlayerActivityTracker playerActivityTracker;
    
    public PlayerJoinListener(Plugin plugin, PlayerHandler playerHandler, PlayerIdMapper playerIdMapper,
                              PlayerActivityTracker playerActivityTracker) {
        this.plugin = plugin;
        this.playerHandler = playerHandler;
        this.playerIdMapper = playerIdMapper;
        this.playerActivityTracker = playerActivityTracker;
    }
    
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        org.bukkit.entity.Player player = event.getPlayer();
        String playerName = player.getName();
        
        // Сначала пытаемся получить player_id из кеша
        playerIdMapper.getPlayerId(player).thenCompose(playerId -> {
            // Регистрируем игрока в трекере онлайна
            if (plugin instanceof CraftSocialPlugin) {
                ((CraftSocialPlugin) plugin).getOnlineTimeTracker().onJoin(player.getUniqueId());
            }
            if (playerActivityTracker != null) {
                playerActivityTracker.onJoin(player.getUniqueId());
            }
            
            // Проверяем существование профиля
            return playerHandler.checkProfileExists(playerId).thenCompose(exists -> {
                if (!exists) {
                    // Профиль не существует - создаем его без player_id
                    logger.info("Profile not found for player {} (UUID: {}), creating new profile...", 
                        playerName, player.getUniqueId());
                    
                    // Создаем профиль, передавая UUID
                    // API требует player_id в теле запроса, метод сам сгенерирует случайное число
                    return playerHandler.createProfile(playerName, player.getUniqueId().toString())
                        .thenCompose(profile -> {
                            if (profile == null) {
                                logger.warn("Failed to create profile for player {}: profile is null", playerName);
                                return java.util.concurrent.CompletableFuture.completedFuture(false);
                            }
                            // Обновляем маппинг с реальным player_id из профиля (может быть 0)
                            long realPlayerId = profile.getPlayerId();
                            playerIdMapper.setPlayerId(player.getUniqueId(), realPlayerId);
                            logger.info("Profile created and player_id mapping updated for {}: {} -> {}",
                                playerName, playerId, realPlayerId);

                            plugin.getServer().getScheduler().runTask(plugin, () -> {
                                MessagePalette.send(player, "§6Ваш профиль создан в системе!");
                            });

                            // Используем СВЕЖИЙ realPlayerId, иначе updateJoinStreak
                            // бьёт на несуществующего игрока и last_login никогда не пишется.
                            return updateJoinStreak(player, realPlayerId);
                        });
                } else {
                    // Профиль существует - обновляем маппинг если нужно
                    return playerHandler.getProfile(playerId).thenCompose(profile -> {
                        long effectiveId = playerId;
                        if (profile != null && profile.getPlayerId() != playerId) {
                            playerIdMapper.setPlayerId(player.getUniqueId(), profile.getPlayerId());
                            effectiveId = profile.getPlayerId();
                            logger.debug("Updated player_id mapping for {}: {} -> {}",
                                playerName, playerId, effectiveId);
                        }
                        return updateJoinStreak(player, effectiveId);
                    });
                }
            });
        }).exceptionally(error -> {
            logger.error("Error processing player join for {}: {}", playerName, error.getMessage(), error);
            return null;
        });
    }
    
    /**
     * Обновляет join_streak_days / join_streak_max и last_login согласно MODELS.md.
     */
    private java.util.concurrent.CompletableFuture<Boolean> updateJoinStreak(org.bukkit.entity.Player player, long playerId) {
        return playerHandler.getAnalytics(playerId).thenCompose(a -> {
            if (a == null) {
                return java.util.concurrent.CompletableFuture.completedFuture(false);
            }
            java.time.OffsetDateTime now = java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC);
            int streak;
            try {
                String lastLogin = a.getLastLogin();
                if (lastLogin == null || lastLogin.isEmpty()) {
                    streak = 1;
                } else {
                    java.time.OffsetDateTime last = java.time.OffsetDateTime.parse(lastLogin);
                    long hours = java.time.Duration.between(last, now).toHours();
                    if (hours < 24) {
                        // Уже заходил сегодня — серию не трогаем
                        streak = a.getJoinStreakDays();
                    } else if (hours <= 48) {
                        streak = a.getJoinStreakDays() + 1;
                    } else {
                        streak = 1;
                    }
                }
            } catch (Exception e) {
                streak = 1;
            }
            if (streak <= 0) streak = 1;
            final int newStreak = streak;
            a.setJoinStreakDays(newStreak);
            if (newStreak > a.getJoinStreakMax()) {
                a.setJoinStreakMax(newStreak);
            }
            a.setLastLogin(now.toString());
            
            return playerHandler.updateAnalytics(playerId, a).thenApply(success -> {
                if (success && newStreak > 1) {
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        MessagePalette.send(player, "§6Серия входов: §6" + newStreak + " §fдней подряд.");
                    });
                }
                return success;
            });
        });
    }
}
