package dev.sh3lby.craftsocial.tasks;

import dev.sh3lby.craftsocial.CraftSocialPlugin;
import dev.sh3lby.craftsocial.storage.ClanInventoryManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;

/**
 * Периодически подтягивает данные с API и обновляет открытые инвентари клана и группы.
 * Клан: getClan → fromApiInventory → putCacheFromApi → обновить сетку у всех зрителей.
 * После forced update (setInventory) 8-тиковый синк с API для этого клана пропускается 8 секунд, чтобы не перезаписать только что сохранённое состояние.
 * Группа: из PartyInventoryManager. Интервал: каждые 8 тиков (CraftSocialPlugin).
 */
public class ClanPartyInventoryRefreshTask extends BukkitRunnable {
    private static final String CLAN_INV_PREFIX = "Инвентарь клана: ";
    private static final String PARTY_INV_PREFIX = "Инвентарь группы: ";
    private static final int INV_SIZE = 54;

    private final CraftSocialPlugin plugin;

    public ClanPartyInventoryRefreshTask(CraftSocialPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        Map<Integer, Player> clanIdToPlayer = new HashMap<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player == null || !player.isOnline()) continue;
            try {
                String title = player.getOpenInventory().getTitle();
                if (title == null) continue;
                if (title.startsWith(CLAN_INV_PREFIX)) {
                    Integer clanId = parseIdAfterPrefix(title, CLAN_INV_PREFIX);
                    if (clanId != null && !clanIdToPlayer.containsKey(clanId)) {
                        clanIdToPlayer.put(clanId, player);
                    }
                } else if (title.startsWith(PARTY_INV_PREFIX)) {
                    Integer partyId = parseIdAfterPrefix(title, PARTY_INV_PREFIX);
                    if (partyId != null) {
                        refreshPartyInventory(player, partyId);
                    }
                }
            } catch (Exception ignored) {
                // Игрок мог закрыть инвентарь или изменить его
            }
        }
        for (Map.Entry<Integer, Player> e : clanIdToPlayer.entrySet()) {
            int clanId = e.getKey();
            if (plugin.getClanInventoryManager().shouldSkipApiSync(clanId)) {
                continue;
            }
            refreshClanInventoryFromApi(clanId, e.getValue());
        }
    }

    private void refreshClanInventoryFromApi(int clanId, Player anyViewer) {
        plugin.getClanHandler().invalidateClanCache(clanId);
        long playerId = plugin.getPlayerIdMapper().getPlayerIdSync(anyViewer);
        plugin.getClanHandler().getClan(clanId, playerId).thenAccept(clan -> {
            if (clan == null) return;
            ItemStack[] contents = ClanInventoryManager.fromApiInventory(clan.getInventory());
            Bukkit.getScheduler().runTask(plugin, () -> {
                plugin.getClanInventoryManager().putCacheFromApi(clanId, contents);
                String expectedTitle = CLAN_INV_PREFIX + clanId;
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (!p.isOnline()) continue;
                    try {
                        if (!expectedTitle.equals(p.getOpenInventory().getTitle())) continue;
                    } catch (Exception ignored) { continue; }
                    Inventory top = p.getOpenInventory().getTopInventory();
                    if (top.getSize() < INV_SIZE) continue;
                    for (int i = 0; i < INV_SIZE && i < contents.length; i++) {
                        top.setItem(i, contents[i] != null ? contents[i].clone() : null);
                    }
                }
            });
        });
    }

    private void refreshPartyInventory(Player player, int partyId) {
        ItemStack[] contents = plugin.getPartyInventoryManager().getInventory(partyId);
        if (contents == null) return;
        Inventory top = player.getOpenInventory().getTopInventory();
        if (top.getSize() < INV_SIZE) return;
        for (int i = 0; i < INV_SIZE && i < contents.length; i++) {
            top.setItem(i, contents[i] != null ? contents[i].clone() : null);
        }
    }

    private static Integer parseIdAfterPrefix(String title, String prefix) {
        String after = title.substring(prefix.length()).trim();
        for (String part : after.split(" ")) {
            try {
                return Integer.parseInt(part);
            } catch (NumberFormatException e) {
                // continue
            }
        }
        return null;
    }
}
