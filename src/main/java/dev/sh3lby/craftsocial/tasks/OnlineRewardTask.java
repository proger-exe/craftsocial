package dev.sh3lby.craftsocial.tasks;

import dev.sh3lby.craftsocial.CraftSocialPlugin;
import dev.sh3lby.craftsocial.config.PluginConfig;
import dev.sh3lby.craftsocial.storage.OnlineTimeTracker;
import dev.sh3lby.craftsocial.storage.PlayerActivityTracker;
import dev.sh3lby.craftsocial.utils.MessagePalette;
import dev.sh3lby.craftsocial.utils.PlayerIdMapper;
import dev.sh3lby.craftsocial.utils.WebhookService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Периодическая задача: каждый час отправляет боту список онлайна игроков.
 * Начисление награды (транзакция) — на стороне бота.
 */
public class OnlineRewardTask extends BukkitRunnable {
    private static final Logger logger = LoggerFactory.getLogger(OnlineRewardTask.class);

    private final CraftSocialPlugin plugin;
    private final OnlineTimeTracker tracker;
    private final PlayerIdMapper playerIdMapper;
    private final WebhookService webhookService;
    private final PluginConfig config;
    private final PlayerActivityTracker activityTracker;

    public OnlineRewardTask(CraftSocialPlugin plugin,
                            OnlineTimeTracker tracker,
                            PlayerActivityTracker activityTracker,
                            PlayerIdMapper playerIdMapper,
                            WebhookService webhookService,
                            PluginConfig config) {
        this.plugin = plugin;
        this.tracker = tracker;
        this.activityTracker = activityTracker;
        this.playerIdMapper = playerIdMapper;
        this.webhookService = webhookService;
        this.config = config;
    }

    @Override
    public void run() {
        if (!config.isOnlineRewardsEnabled()) {
            return;
        }
        List<Map<String, Object>> batchForBot = new ArrayList<>();

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player == null || !player.isOnline()) continue;
            try {
                if (config.isOnlineRewardsAfkProtectionEnabled()) {
                    long afkTimeoutMillis = Math.max(0L, config.getOnlineRewardsAfkTimeoutSeconds()) * 1000L;
                    if (activityTracker != null && activityTracker.isAfk(player.getUniqueId(), afkTimeoutMillis)) {
                        logger.debug("Skipping hourly playtime for {}: AFK for {} ms",
                            player.getName(), activityTracker.getIdleMillis(player.getUniqueId()));
                        continue;
                    }
                }
                int hour = tracker.claimCompletedHour(player.getUniqueId());
                if (hour == 0) continue;
                long playerId = playerIdMapper.getPlayerIdSync(player);
                int playtimeSeconds = hour * 3600;
                Map<String, Object> entry = new HashMap<>();
                entry.put("player_id", playerId);
                entry.put("playtime_seconds", playtimeSeconds);
                batchForBot.add(entry);
                logger.info("Hourly playtime sent for {} (player_id={}, hour {})",
                    player.getName(), playerId, hour);
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    MessagePalette.send(player, "§6Час онлайн учтён, награду начислит бот.");
                });
            } catch (Exception e) {
                logger.warn("Error processing hourly playtime for player {}", player.getName(), e);
            }
        }

        if (!batchForBot.isEmpty() && webhookService != null) {
            webhookService.pushPlaytimeRewardBatch(batchForBot);
        }
    }
}
