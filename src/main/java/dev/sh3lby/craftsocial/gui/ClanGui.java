package dev.sh3lby.craftsocial.gui;

import dev.sh3lby.craftsocial.handlers.ClanHandler;
import dev.sh3lby.craftsocial.models.Clan;
import dev.sh3lby.craftsocial.models.ClanMember;
import dev.sh3lby.craftsocial.utils.MessagePalette;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;

/**
 * GUI для клана.
 */
public class ClanGui {
    private final ClanHandler clanHandler;
    private final Plugin plugin;
    
    public ClanGui(ClanHandler clanHandler, Plugin plugin) {
        this.clanHandler = clanHandler;
        this.plugin = plugin;
    }
    
    /**
     * Открывает GUI клана для игрока.
     */
    public void openClan(Player player, int clanId) {
        long playerId = ((dev.sh3lby.craftsocial.CraftSocialPlugin) plugin).getPlayerIdMapper().getPlayerIdSync(player);
        
        // Получаем данные клана асинхронно
        clanHandler.getClan(clanId, playerId).thenAccept(clan -> {
            if (clan == null) {
                MessagePalette.send(player, "Клан не найден");
                return;
            }
            dev.sh3lby.craftsocial.CraftSocialPlugin js = (dev.sh3lby.craftsocial.CraftSocialPlugin) plugin;
            java.util.Map<Long, String> names = new java.util.concurrent.ConcurrentHashMap<>();
            List<ClanMember> members = clan.getMembers() != null ? clan.getMembers() : new ArrayList<>();
            for (ClanMember m : members) {
                String n = getOnlineDisplayName(js, m.getPlayerId());
                if (n != null) names.put(m.getPlayerId(), n);
            }
            List<ClanMember> needFetch = members.stream()
                .filter(m -> !names.containsKey(m.getPlayerId())).collect(java.util.stream.Collectors.toList());
            if (!needFetch.isEmpty()) {
                java.util.concurrent.CompletableFuture<?>[] futures = needFetch.stream()
                    .map(m -> js.getPlayerHandler().getProfile(m.getPlayerId())
                        .thenAccept(p -> names.put(m.getPlayerId(), p != null && p.getNickname() != null ? p.getNickname() : ("#" + m.getPlayerId()))))
                    .toArray(java.util.concurrent.CompletableFuture[]::new);
                java.util.concurrent.CompletableFuture.allOf(futures).thenRun(() ->
                    plugin.getServer().getScheduler().runTask(plugin, () -> openClanWithNames(player, clan, names)));
            } else {
                plugin.getServer().getScheduler().runTask(plugin, () -> openClanWithNames(player, clan, names));
            }
        }).exceptionally(error -> {
            MessagePalette.send(player, "§cОшибка загрузки клана: §f" + error.getMessage());
            return null;
        });
    }

    private static String getOnlineDisplayName(dev.sh3lby.craftsocial.CraftSocialPlugin plugin, long memberPlayerId) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (plugin.getPlayerIdMapper().getPlayerIdSync(p) == memberPlayerId) {
                return p.getName();
            }
        }
        return null;
    }

    private void openClanWithNames(Player player, Clan clan, java.util.Map<Long, String> memberNames) {
        Inventory inventory = Bukkit.createInventory(null, 54, "Клан: " + clan.getName());

        ItemStack infoItem = new ItemStack(Material.BOOK);
        ItemMeta infoMeta = infoItem.getItemMeta();
        if (infoMeta != null) {
            infoMeta.setDisplayName(MessagePalette.paint("§c" + clan.getName() + " [" + clan.getTag() + "]"));
            List<String> lore = new ArrayList<>();
            lore.add("§fОписание: " + (clan.getDescription() != null ? clan.getDescription() : ""));
            lore.add("§fПрефикс: " + (clan.getPrefix() != null ? clan.getPrefix() : ""));
            lore.add("§fУчастников: §6" + (clan.getMembers() != null ? clan.getMembers().size() : 0));
            infoMeta.setLore(MessagePalette.paint(lore));
            infoItem.setItemMeta(infoMeta);
        }
        inventory.setItem(4, infoItem);

        if (clan.getMembers() != null) {
            int slot = 9;
            for (ClanMember member : clan.getMembers()) {
                if (slot >= 45) break;
                String displayName = memberNames.getOrDefault(member.getPlayerId(), "#" + member.getPlayerId());

                ItemStack memberItem = new ItemStack(Material.PLAYER_HEAD);
                ItemMeta memberMeta = memberItem.getItemMeta();
                if (memberMeta != null) {
                    memberMeta.setDisplayName(MessagePalette.paint("§c" + displayName));
                    List<String> memberLore = new ArrayList<>();
                    memberLore.add("§fРоль: §6" + member.getRole());
                    memberLore.add("§fПрисоединился: " + (member.getJoinedAt() != null ? member.getJoinedAt() : ""));
                    memberMeta.setLore(MessagePalette.paint(memberLore));
                    memberItem.setItemMeta(memberMeta);
                }
                inventory.setItem(slot++, memberItem);
            }
        }

        player.openInventory(inventory);
    }
}
