package dev.sh3lby.craftsocial.listeners;

import dev.sh3lby.craftsocial.CraftSocialPlugin;
import dev.sh3lby.craftsocial.gui.ClanManagementGui;
import dev.sh3lby.craftsocial.gui.PartyManagementGui;
import dev.sh3lby.craftsocial.gui.ShopGui;
import dev.sh3lby.craftsocial.gui.ShopManagementGui;
import dev.sh3lby.craftsocial.utils.InventoryMetadata;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.Bukkit;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * Слушатель событий инвентаря для обработки GUI.
 */
public class InventoryListener implements Listener {
    private final CraftSocialPlugin plugin;
    
    public InventoryListener(CraftSocialPlugin plugin) {
        this.plugin = plugin;
    }
    
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        
        Player player = (Player) event.getWhoClicked();
        Inventory inventory = event.getInventory();
        String title = event.getView().getTitle();
        if (title == null) return;
        
        // Обработка GUI информации о клане (/clan info) — клики не передаём в инвентарь
        if (title.startsWith("Клан: ")) {
            event.setCancelled(true);
            return;
        }
        
        // Обработка GUI магазина (парсим shopId из заголовка, не полагаемся на getViewers)
        if (title.startsWith("Магазин: ")) {
            event.setCancelled(true);
            if (event.getCurrentItem() != null) {
                int[] meta = parseShopMeta(title, "Магазин: ");
                if (meta != null) {
                    ShopGui shopGui = new ShopGui(plugin.getShopHandler(), plugin);
                    shopGui.handleClick(player, event.getSlot(), event.getCurrentItem(), meta[0], meta[1]);
                }
            }
            return;
        }

        if (title.startsWith("Инвентарь магазина: ")) {
            int[] meta = parseShopMeta(title, "Инвентарь магазина: ");
            if (meta == null) {
                event.setCancelled(true);
                return;
            }
            int rawSlot = event.getRawSlot();
            int topSize = event.getView().getTopInventory().getSize();
            if (rawSlot >= 0 && rawSlot < topSize) {
                if (ShopGui.isOwnerInventoryControlSlot(rawSlot)) {
                    event.setCancelled(true);
                    ShopGui shopGui = new ShopGui(plugin.getShopHandler(), plugin);
                    shopGui.handleOwnerInventoryClick(player, rawSlot, event.getCurrentItem(), meta[0], meta[1]);
                    return;
                }
                event.setCancelled(false);
                return;
            }
            event.setCancelled(false);
            return;
        }
        
        // Обработка GUI управления кланом (парсим clanId из заголовка)
        if (title.startsWith("Управление клана: ")) {
            event.setCancelled(true);
            if (event.getCurrentItem() != null) {
                Integer clanId = parseFirstIntFromTitle(title.substring("Управление клана: ".length()).trim());
                ClanManagementGui clanManagementGui = new ClanManagementGui(plugin.getClanHandler(), plugin.getClanInventoryManager(), plugin);
                clanManagementGui.handleClick(player, event.getSlot(), event.getCurrentItem(), clanId);
            }
            return;
        }
        
        // Обработка GUI домов клана (парсим clanId из заголовка)
        if (title.startsWith("Дома клана: ")) {
            event.setCancelled(true);
            if (event.getCurrentItem() != null) {
                Integer clanId = parseFirstIntFromTitle(title.substring("Дома клана: ".length()).trim());
                ClanManagementGui clanManagementGui = new ClanManagementGui(plugin.getClanHandler(), plugin.getClanInventoryManager(), plugin);
                clanManagementGui.handleHomesClick(player, event.getSlot(), event.getCurrentItem(), clanId, event.isRightClick());
            }
            return;
        }
        
        // Обработка инвентаря клана (разрешаем перемещение, сохраняем при закрытии; сразу синхронизируем с другими зрителями)
        if (title.startsWith("Инвентарь клана: ")) {
            event.setCancelled(false);
            String titleCopy = title;
            Player whoClicked = player;
            int rawSlot = event.getRawSlot();
            // только слот верхнего инвентаря (0..53) учитываем для курсора
            int clickedRawSlot = (rawSlot >= 0 && rawSlot < 54) ? rawSlot : -1;
            Bukkit.getScheduler().runTask(plugin, () -> syncClanOrPartyInventoryToOthers(whoClicked, titleCopy, true, clickedRawSlot));
            return;
        }
        
        // Обработка GUI управления магазином
        if (title.startsWith("Управление магазином: ")) {
            event.setCancelled(true);
            if (event.getCurrentItem() != null) {
                int[] meta = parseShopMeta(title, "Управление магазином: ");
                if (meta != null) {
                    ShopManagementGui shopManagementGui = new ShopManagementGui(plugin.getShopHandler(), plugin);
                    shopManagementGui.handleClick(player, event.getSlot(), event.getCurrentItem(), meta[0], meta[1], event.isRightClick(), event.isShiftClick());
                } else {
                    ShopManagementGui shopManagementGui = new ShopManagementGui(plugin.getShopHandler(), plugin);
                    shopManagementGui.handleClick(player, event.getSlot(), event.getCurrentItem(), null, 0, event.isRightClick(), event.isShiftClick());
                }
            }
            return;
        }
        
        // Обработка GUI управления группой
        if (title.startsWith("Управление группы: ") || title.startsWith("Управление: ")) {
            event.setCancelled(true);
            if (event.getCurrentItem() != null) {
                Integer partyId = parseFirstIntFromTitle(title);
                if (partyId == null) partyId = InventoryMetadata.getPartyId(inventory);
                PartyManagementGui partyManagementGui = new PartyManagementGui(
                    plugin.getPartyHandler(), 
                    plugin.getPartyInventoryManager(), 
                    plugin
                );
                partyManagementGui.handleClick(player, event.getSlot(), event.getCurrentItem(), partyId);
            }
            return;
        }
        
        // Обработка инвентаря группы (в т.ч. "Инвентарь группы: <partyId>")
        if (title != null && title.startsWith("Инвентарь группы")) {
            event.setCancelled(false);
            String titleCopy = title;
            Player whoClicked = player;
            Bukkit.getScheduler().runTask(plugin, () -> syncClanOrPartyInventoryToOthers(whoClicked, titleCopy, false, -1));
            return;
        }
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        String title = event.getView().getTitle();
        if (title == null) return;
        if (title.startsWith("Инвентарь магазина: ")) {
            for (int rawSlot : event.getRawSlots()) {
                if (ShopGui.isOwnerInventoryControlSlot(rawSlot)) {
                    event.setCancelled(true);
                    return;
                }
            }
            event.setCancelled(false);
            return;
        }
        if (!title.startsWith("Инвентарь клана: ")) return;
        event.setCancelled(false);
        String titleCopy = title;
        Player whoClicked = (Player) event.getWhoClicked();
        Bukkit.getScheduler().runTask(plugin, () -> syncClanOrPartyInventoryToOthers(whoClicked, titleCopy, true, -1));
    }

    /**
     * Выполняется на следующем тике после клика/драга: снимает снимок (сетка 54 + курсор), обновляет локальный кеш.
     * Для клана рассылка зрителям выполняется с задержкой 2 тика (только сетка 0..53).
     */
    private void syncClanOrPartyInventoryToOthers(Player whoClicked, String title, boolean isClan, int clickedRawSlot) {
        if (!whoClicked.isOnline()) return;
        if (!title.equals(whoClicked.getOpenInventory().getTitle())) return;
        Inventory top = whoClicked.getOpenInventory().getTopInventory();
        if (top.getSize() < 54) return;

        ItemStack[] contents = new ItemStack[54];
        for (int i = 0; i < 54; i++) {
            ItemStack item = top.getItem(i);
            contents[i] = item != null ? item.clone() : null;
        }
        ItemStack cursor = (isClan) ? whoClicked.getItemOnCursor() : null;
        if (cursor != null && (cursor.getType().isAir() || cursor.isEmpty())) cursor = null;
        else if (cursor != null) cursor = cursor.clone();

        if (isClan) {
            Integer clanId = parseFirstIntFromTitle(title.substring("Инвентарь клана: ".length()).trim());
            if (clanId != null) {
                long playerId = plugin.getPlayerIdMapper().getPlayerIdSync(whoClicked);
                plugin.getClanInventoryManager().setInventory(clanId, contents, cursor, playerId, savedGrid -> {
                    if (savedGrid == null) return;
                    Bukkit.getScheduler().runTaskLater(plugin, () -> broadcastClanGridToOtherViewers(clanId, title, whoClicked, savedGrid), 2L);
                });
            }
        } else {
            Integer partyId = null;
            if (title.length() > "Инвентарь группы".length() && title.contains(":")) {
                String after = title.substring(title.indexOf(':') + 1).trim();
                partyId = parseFirstIntFromTitle(after);
            }
            if (partyId != null) {
                plugin.getPartyInventoryManager().setInventory(partyId, contents);
            }
        }
    }

    /**
     * На главном потоке (вызывать через runTaskLater): обновляет верхнюю сетку 0..53 у всех остальных зрителей инвентаря клана.
     */
    private void broadcastClanGridToOtherViewers(int clanId, String title, Player whoClicked, ItemStack[] grid) {
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other.equals(whoClicked)) continue;
            if (!other.isOnline()) continue;
            try {
                if (!title.equals(other.getOpenInventory().getTitle())) continue;
            } catch (Exception ignored) { continue; }
            Inventory otherTop = other.getOpenInventory().getTopInventory();
            if (otherTop.getSize() < 54) continue;
            for (int i = 0; i < 54; i++) {
                otherTop.setItem(i, grid[i] != null ? grid[i].clone() : null);
            }
        }
    }
    
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) {
            return;
        }
        
        Player player = (Player) event.getPlayer();
        Inventory inventory = event.getInventory();
        String title = event.getView().getTitle();
        
        if (title != null && title.startsWith("Инвентарь группы")) {
            Integer partyIdFromTitle = null;
            if (title.length() > "Инвентарь группы".length() && title.contains(":")) {
                String after = title.substring(title.indexOf(':') + 1).trim();
                partyIdFromTitle = parseFirstIntFromTitle(after);
            }
            if (partyIdFromTitle != null) {
                PartyManagementGui partyManagementGui = new PartyManagementGui(
                    plugin.getPartyHandler(),
                    plugin.getPartyInventoryManager(),
                    plugin
                );
                partyManagementGui.saveInventory(player, partyIdFromTitle, inventory);
            } else {
                long playerId = plugin.getPlayerIdMapper().getPlayerIdSync(player);
                plugin.getPlayerHandler().getAnalytics(playerId).thenAccept(analytics -> {
                    if (analytics != null && analytics.getPartyId() != null) {
                        PartyManagementGui partyManagementGui = new PartyManagementGui(
                            plugin.getPartyHandler(),
                            plugin.getPartyInventoryManager(),
                            plugin
                        );
                        partyManagementGui.saveInventory(player, analytics.getPartyId(), inventory);
                    }
                });
            }
        }
        
        if (title != null && title.startsWith("Инвентарь клана: ")) {
            String after = title.substring("Инвентарь клана: ".length()).trim();
            Integer clanId = parseFirstIntFromTitle(after);
            if (clanId != null) {
                ClanManagementGui clanManagementGui = new ClanManagementGui(plugin.getClanHandler(), plugin.getClanInventoryManager(), plugin);
                clanManagementGui.saveClanInventory(player, clanId, inventory);
            }
        }

        if (title != null && title.startsWith("Инвентарь магазина: ")) {
            if (ShopGui.consumeOwnerInventoryTransition(player, title)) {
                return;
            }
            int[] meta = parseShopMeta(title, "Инвентарь магазина: ");
            if (meta != null && plugin.getShopLocationManager().getShopLocation(meta[0]) != null) {
                ShopGui shopGui = new ShopGui(plugin.getShopHandler(), plugin);
                shopGui.saveOwnerInventoryPage(player, meta[0], meta[1], inventory);
            }
        }
    }
    
    /**
     * Извлекает первое целое число из строки (первый токен или первое число среди токенов).
     */
    private static Integer parseFirstIntFromTitle(String s) {
        if (s == null || s.isEmpty()) return null;
        for (String part : s.split(" ")) {
            try {
                return Integer.parseInt(part);
            } catch (NumberFormatException e) {
                // continue
            }
        }
        return null;
    }

    private static int[] parseShopMeta(String title, String prefix) {
        if (title == null || !title.startsWith(prefix)) {
            return null;
        }
        String raw = title.substring(prefix.length()).trim();
        if (raw.isEmpty()) {
            return null;
        }
        String firstToken = raw.split(" ")[0];
        String[] parts = firstToken.split(":");
        if (parts.length != 2) {
            return null;
        }
        try {
            return new int[] { Integer.parseInt(parts[0]), Integer.parseInt(parts[1]) };
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
