package dev.sh3lby.craftsocial.listeners;

import dev.sh3lby.craftsocial.CraftSocialPlugin;
import dev.sh3lby.craftsocial.storage.OnlineTimeTracker;
import dev.sh3lby.craftsocial.storage.PlayerActivityTracker;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Очищает трекер онлайна при выходе игрока.
 */
public class PlayerQuitListener implements Listener {
    private final OnlineTimeTracker tracker;
    private final PlayerActivityTracker activityTracker;
    
    public PlayerQuitListener(CraftSocialPlugin plugin, OnlineTimeTracker tracker, PlayerActivityTracker activityTracker) {
        this.tracker = tracker;
        this.activityTracker = activityTracker;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (player == null) return;
        tracker.onQuit(player.getUniqueId());
        if (activityTracker != null) {
            activityTracker.onQuit(player.getUniqueId());
        }
    }
}
