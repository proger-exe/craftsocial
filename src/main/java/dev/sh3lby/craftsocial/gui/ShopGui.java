package dev.sh3lby.craftsocial.gui;

import dev.sh3lby.craftsocial.handlers.ShopHandler;
import dev.sh3lby.craftsocial.models.Shop;
import dev.sh3lby.craftsocial.models.ShopItem;
import dev.sh3lby.craftsocial.storage.ShopInventoryManager;
import dev.sh3lby.craftsocial.utils.InventoryMetadata;
import dev.sh3lby.craftsocial.utils.ItemNbtSupport;
import dev.sh3lby.craftsocial.utils.MessagePalette;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.BlockState;
import org.bukkit.block.DoubleChest;
import org.bukkit.block.ShulkerBox;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;

/**
 * GUI магазина: витрина покупателя и виртуальный инвентарь владельца.
 */
public class ShopGui {
    private static final Logger logger = LoggerFactory.getLogger(ShopGui.class);
    private static final int DISPLAY_INVENTORY_SIZE = 54;
    private static final int ITEMS_PER_PAGE = 45;
    private static final int PREV_SLOT = 45;
    private static final int INFO_SLOT = 49;
    private static final int NEXT_SLOT = 53;
    private static final long TRANSITION_TTL_MILLIS = 10_000L;
    private static final Map<UUID, OwnerInventoryTransition> OWNER_PAGE_TRANSITIONS = new ConcurrentHashMap<>();

    private final ShopHandler shopHandler;
    private final Plugin plugin;

    private static final class OwnerInventoryTransition {
        private final String sourceTitle;
        private final long expiresAtMillis;

        private OwnerInventoryTransition(String sourceTitle, long expiresAtMillis) {
            this.sourceTitle = sourceTitle;
            this.expiresAtMillis = expiresAtMillis;
        }

        private boolean matches(String title, long nowMillis) {
            return sourceTitle != null && sourceTitle.equals(title) && nowMillis <= expiresAtMillis;
        }
    }

    public ShopGui(ShopHandler shopHandler, Plugin plugin) {
        this.shopHandler = shopHandler;
        this.plugin = plugin;
    }

    public void openShop(Player player, int shopId) {
        openShop(player, shopId, 0);
    }

    public void openShop(Player player, int shopId, int page) {
        long playerId = ((dev.sh3lby.craftsocial.CraftSocialPlugin) plugin).getPlayerIdMapper().getPlayerIdSync(player);
        shopHandler.invalidateShopCache(shopId);
        shopHandler.getShopLookup(shopId, playerId).thenAccept(result -> {
            Shop shop = result.getShop();
            if (shop == null) {
                MessagePalette.send(player, "Магазин не найден");
                return;
            }

            final boolean isOwner = shop.getOwnerId() == playerId;
            final List<ShopItem> visibleItems = new ArrayList<>();
            if (shop.getItems() != null) {
                for (ShopItem item : shop.getItems()) {
                    if (!isOwner && item.getTotalCount() <= 0) {
                        continue;
                    }
                    visibleItems.add(item);
                }
            }

            final int safePage = clampPage(page, visibleItems.size(), ITEMS_PER_PAGE);
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                Inventory inventory = Bukkit.createInventory(null, DISPLAY_INVENTORY_SIZE, buyerTitle(shop.getId(), safePage, shop.getName()));
                populateShopPage(inventory, visibleItems, safePage);
                setNavigation(inventory, safePage, visibleItems.size(), Material.ARROW);

                if (isOwner) {
                    ItemStack openInventory = new ItemStack(Material.CHEST);
                    ItemMeta meta = openInventory.getItemMeta();
                    if (meta != null) {
                        meta.setDisplayName(MessagePalette.paint("§cОткрыть инвентарь магазина"));
                        meta.setLore(MessagePalette.paint(List.of("§fОткрыть виртуальный инвентарь магазина")));
                        openInventory.setItemMeta(meta);
                    }
                    inventory.setItem(INFO_SLOT, openInventory);
                } else {
                    inventory.setItem(INFO_SLOT, pageInfoItem("Витрина магазина", safePage, visibleItems.size()));
                }

                player.openInventory(inventory);
            });
        }).exceptionally(error -> {
            MessagePalette.send(player, "§cОшибка загрузки магазина: §f" + error.getMessage());
            return null;
        });
    }

    public void handleClick(Player player, int slot, ItemStack clickedItem, int shopId, int page) {
        if (clickedItem == null || clickedItem.getType() == Material.AIR) {
            return;
        }

        if (slot == PREV_SLOT) {
            openShop(player, shopId, Math.max(0, page - 1));
            return;
        }
        if (slot == NEXT_SLOT) {
            openShop(player, shopId, page + 1);
            return;
        }
        if (slot == INFO_SLOT && clickedItem.getType() == Material.CHEST) {
            beginOwnerInventoryTransition(player, player.getOpenInventory().getTitle());
            plugin.getServer().getScheduler().runTask(plugin, () -> openOwnerInventory(player, shopId, 0));
            return;
        }

        Integer itemId = InventoryMetadata.getItemId(clickedItem, plugin);
        if (itemId == null) {
            MessagePalette.sendRaw(player, "§cНе удалось определить ID товара");
            return;
        }

        long playerId = ((dev.sh3lby.craftsocial.CraftSocialPlugin) plugin).getPlayerIdMapper().getPlayerIdSync(player);
        shopHandler.invalidateShopCache(shopId);
        shopHandler.getShopLookup(shopId, playerId).thenCompose(result -> {
            Shop shop = result.getShop();
            if (shop == null || shop.getItems() == null) {
                MessagePalette.sendRaw(player, "§cТовар не найден");
                return CompletableFuture.completedFuture(false);
            }

            ShopItem selected = shop.getItems().stream()
                .filter(i -> i.getId() == itemId)
                .findFirst()
                .orElse(null);
            if (selected == null) {
                MessagePalette.sendRaw(player, "§cТовар не найден");
                return CompletableFuture.completedFuture(false);
            }

            int lotSize = Math.max(1, selected.getQuantity());
            return shopHandler.purchaseItem(player, shopId, itemId, lotSize);
        }).thenAccept(success -> {
            if (success) {
                plugin.getServer().getScheduler().runTask(plugin, () -> player.closeInventory());
            }
        });
    }

    public void openOwnerInventory(Player player, int shopId, int page) {
        if (player == null || !player.isOnline()) {
            return;
        }
        long playerId = ((dev.sh3lby.craftsocial.CraftSocialPlugin) plugin).getPlayerIdMapper().getPlayerIdSync(player);
        shopHandler.invalidateShopCache(shopId);
        shopHandler.awaitPendingChestSync(shopId)
            .thenCompose(ignored -> shopHandler.getShopLookup(shopId, playerId))
            .thenCompose(result -> {
                Shop shop = result.getShop();
                if (shop == null) {
                    MessagePalette.sendRaw(player, "§cМагазин не найден");
                    return CompletableFuture.completedFuture(null);
                }
                if (shop.getOwnerId() != playerId) {
                    MessagePalette.sendRaw(player, "§cИнвентарь магазина может открыть только владелец.");
                    return CompletableFuture.completedFuture(null);
                }
                return resolveDesiredInventorySizeAsync(shopId).thenApply(desiredSize -> {
                    ItemStack[] contents = ((dev.sh3lby.craftsocial.CraftSocialPlugin) plugin)
                        .getShopInventoryManager()
                        .loadOrBootstrapFromApi(shopId, shop, desiredSize);
                    if (contents == null) {
                        logger.warn("Owner inventory bootstrap returned null for shop {}", shopId);
                        MessagePalette.sendRaw(player, "§cНе удалось загрузить инвентарь магазина.");
                        return null;
                    }
                    return new OwnerInventoryOpenData(shopId, page, contents);
                });
            })
            .thenAccept(openData -> {
                if (openData == null) {
                    return;
                }
                int safePage = clampPage(openData.page(), openData.contents().length, ITEMS_PER_PAGE);
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (!player.isOnline()) {
                        return;
                    }
                    Inventory inventory = Bukkit.createInventory(null, DISPLAY_INVENTORY_SIZE, ownerInventoryTitle(openData.shopId(), safePage));
                    fillOwnerControlRow(inventory);
                    int start = safePage * ITEMS_PER_PAGE;
                    for (int i = 0; i < ITEMS_PER_PAGE && start + i < openData.contents().length; i++) {
                        ItemStack item = openData.contents()[start + i];
                        if (item != null) {
                            inventory.setItem(i, item.clone());
                        }
                    }
                    inventory.setItem(INFO_SLOT, pageInfoItem("Инвентарь магазина", safePage, openData.contents().length));
                    setNavigation(inventory, safePage, openData.contents().length, Material.SPECTRAL_ARROW);
                    player.openInventory(inventory);
                });
            })
            .exceptionally(error -> {
                logger.error("Failed to open owner inventory for shop {}", shopId, error);
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (player.isOnline()) {
                        MessagePalette.sendRaw(player, "§cОшибка открытия инвентаря магазина.");
                    }
                });
                return null;
            });
    }

    public void handleOwnerInventoryClick(Player player, int slot, ItemStack clickedItem, int shopId, int page) {
        if (slot == PREV_SLOT) {
            persistOwnerInventoryPageLocally(shopId, page, player.getOpenInventory().getTopInventory());
            beginOwnerInventoryTransition(player, ownerInventoryTitle(shopId, page));
            openOwnerInventory(player, shopId, Math.max(0, page - 1));
            return;
        }
        if (slot == NEXT_SLOT) {
            persistOwnerInventoryPageLocally(shopId, page, player.getOpenInventory().getTopInventory());
            beginOwnerInventoryTransition(player, ownerInventoryTitle(shopId, page));
            openOwnerInventory(player, shopId, page + 1);
            return;
        }
    }

    public void saveOwnerInventoryPage(Player player, int shopId, int page, Inventory inventory) {
        long playerId = ((dev.sh3lby.craftsocial.CraftSocialPlugin) plugin).getPlayerIdMapper().getPlayerIdSync(player);
        ItemStack[] full = persistOwnerInventoryPageLocally(shopId, page, inventory);
        if (full == null) {
            return;
        }
        shopHandler.enqueueChestSync(shopId, () -> shopHandler.syncInventorySnapshot(shopId, playerId, full));
    }

    public static boolean consumeOwnerInventoryTransition(Player player, String closingTitle) {
        if (player == null || closingTitle == null) {
            return false;
        }

        UUID playerId = player.getUniqueId();
        long nowMillis = System.currentTimeMillis();
        OwnerInventoryTransition transition = OWNER_PAGE_TRANSITIONS.get(playerId);
        if (transition == null) {
            return false;
        }
        if (!transition.matches(closingTitle, nowMillis)) {
            if (nowMillis > transition.expiresAtMillis) {
                OWNER_PAGE_TRANSITIONS.remove(playerId, transition);
            }
            return false;
        }
        return OWNER_PAGE_TRANSITIONS.remove(playerId, transition);
    }

    private record OwnerInventoryOpenData(int shopId, int page, ItemStack[] contents) {
    }

    private static void beginOwnerInventoryTransition(Player player, String sourceTitle) {
        if (player == null || sourceTitle == null) {
            return;
        }
        OWNER_PAGE_TRANSITIONS.put(player.getUniqueId(),
            new OwnerInventoryTransition(sourceTitle, System.currentTimeMillis() + TRANSITION_TTL_MILLIS));
    }

    private ItemStack[] persistOwnerInventoryPageLocally(int shopId, int page, Inventory inventory) {
        if (inventory == null) {
            return null;
        }

        int desiredSize = resolveDesiredInventorySize(shopId);
        ShopInventoryManager manager = ((dev.sh3lby.craftsocial.CraftSocialPlugin) plugin).getShopInventoryManager();
        ItemStack[] full = manager.getInventory(shopId, desiredSize);
        int start = page * ITEMS_PER_PAGE;
        for (int i = 0; i < ITEMS_PER_PAGE && start + i < full.length; i++) {
            ItemStack item = inventory.getItem(i);
            full[start + i] = item != null ? item.clone() : null;
        }
        manager.setInventory(shopId, full, desiredSize);
        return full;
    }

    public static String buyerTitle(int shopId, int page, String name) {
        return "Магазин: " + shopId + ":" + page + " " + name;
    }

    public static String ownerInventoryTitle(int shopId, int page) {
        return "Инвентарь магазина: " + shopId + ":" + page;
    }

    public static boolean isOwnerInventoryControlSlot(int slot) {
        return slot == PREV_SLOT || slot == INFO_SLOT || slot == NEXT_SLOT;
    }

    public static boolean isOwnerInventoryDataSlot(int slot) {
        return slot >= 0 && slot < ITEMS_PER_PAGE;
    }

    private void populateShopPage(Inventory inventory, List<ShopItem> items, int page) {
        int start = page * ITEMS_PER_PAGE;
        int slot = 0;
        for (int index = start; index < items.size() && slot < ITEMS_PER_PAGE; index++) {
            inventory.setItem(slot++, createShopItemStack(items.get(index)));
        }
    }

    private void setNavigation(Inventory inventory, int page, int totalItems, Material icon) {
        int totalPages = Math.max(1, (int) Math.ceil(totalItems / (double) ITEMS_PER_PAGE));
        if (page > 0) {
            inventory.setItem(PREV_SLOT, navigationItem(icon, "Предыдущая страница", page, totalPages));
        }
        if (page + 1 < totalPages) {
            inventory.setItem(NEXT_SLOT, navigationItem(icon, "Следующая страница", page, totalPages));
        }
    }

    private ItemStack navigationItem(Material material, String label, int page, int totalPages) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(MessagePalette.paint("§6" + label));
            meta.setLore(MessagePalette.paint(List.of("§fСтраница: §6" + (page + 1) + "/" + totalPages)));
            item.setItemMeta(meta);
        }
        return item;
    }

    private void fillOwnerControlRow(Inventory inventory) {
        inventory.setItem(PREV_SLOT, disabledControlItem("Предыдущая страница"));
        inventory.setItem(INFO_SLOT, disabledControlItem("Инвентарь магазина"));
        inventory.setItem(NEXT_SLOT, disabledControlItem("Следующая страница"));
    }

    private ItemStack disabledControlItem(String label) {
        ItemStack item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(MessagePalette.paint("§f" + label));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack pageInfoItem(String label, int page, int totalItems) {
        ItemStack item = new ItemStack(Material.BOOK);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            int totalPages = Math.max(1, (int) Math.ceil(totalItems / (double) ITEMS_PER_PAGE));
            meta.setDisplayName(MessagePalette.paint("§c" + label));
            meta.setLore(MessagePalette.paint(List.of(
                "§fСтраница: §6" + (page + 1) + "/" + totalPages,
                "§fВсего слотов: §6" + totalItems
            )));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createShopItemStack(ShopItem shopItem) {
        int displayAmount = Math.max(1, Math.min(64, shopItem.getQuantity()));
        String nbt = (shopItem.getItemNbt() != null && !shopItem.getItemNbt().isBlank()) ? shopItem.getItemNbt() : null;
        ItemStack item = ItemNbtSupport.fromNbtString(shopItem.getItemId(), nbt, displayAmount);
        if (item == null || item.getType() == Material.AIR) {
            Material material = Material.matchMaterial(shopItem.getItemId());
            item = new ItemStack(material != null ? material : Material.BARRIER, displayAmount);
        }
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(MessagePalette.paint("§c" + toReadableItemName(item.getType(), shopItem.getItemId())));
            List<String> lore = new ArrayList<>();
            lore.add("§fЦена за 1 шт: §6" + shopItem.getPrice() + " вайши");
            lore.add("§fРазмер лота: §6" + shopItem.getQuantity());
            lore.add("§fОстаток: §6" + shopItem.getTotalCount());
            lore.add("");
            lore.add("§6Нажмите для покупки лота (" + shopItem.getQuantity() + " шт.)");
            meta.setLore(MessagePalette.paint(lore));
            item.setItemMeta(meta);
            InventoryMetadata.setItemId(item, plugin, shopItem.getId());
        }
        return item;
    }

    private static String toReadableItemName(Material material, String fallbackId) {
        if (material == null || material == Material.BARRIER) {
            return fallbackId;
        }
        String raw = material.getKey().getKey();
        String[] parts = raw.split("_");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1));
        }
        return sb.toString();
    }

    private int resolveDesiredInventorySize(int shopId) {
        if (!Bukkit.isPrimaryThread()) {
            logger.warn("resolveDesiredInventorySize called off main thread for shop {}", shopId);
            return ShopInventoryManager.DEFAULT_INVENTORY_SIZE;
        }
        org.bukkit.Location loc = ((dev.sh3lby.craftsocial.CraftSocialPlugin) plugin).getShopLocationManager().getShopLocation(shopId);
        if (loc == null || loc.getWorld() == null) {
            return ShopInventoryManager.DEFAULT_INVENTORY_SIZE;
        }
        BlockState state = loc.getWorld().getBlockAt(loc).getState();
        if (state instanceof org.bukkit.block.Chest && ((org.bukkit.block.Chest) state).getInventory().getHolder() instanceof DoubleChest) {
            return ShopInventoryManager.DOUBLE_CHEST_INVENTORY_SIZE;
        }
        if (state instanceof org.bukkit.block.Barrel || state instanceof ShulkerBox) {
            return ShopInventoryManager.DEFAULT_INVENTORY_SIZE;
        }
        return ShopInventoryManager.DEFAULT_INVENTORY_SIZE;
    }

    private CompletableFuture<Integer> resolveDesiredInventorySizeAsync(int shopId) {
        CompletableFuture<Integer> future = new CompletableFuture<>();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            try {
                future.complete(resolveDesiredInventorySize(shopId));
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    private int clampPage(int requestedPage, int totalItems, int pageSize) {
        if (requestedPage <= 0) {
            return 0;
        }
        int maxPage = Math.max(0, (int) Math.ceil(totalItems / (double) pageSize) - 1);
        return Math.min(requestedPage, maxPage);
    }
}
