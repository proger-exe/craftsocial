package dev.sh3lby.craftsocial.gui;

import dev.sh3lby.craftsocial.handlers.ShopHandler;
import dev.sh3lby.craftsocial.models.Shop;
import dev.sh3lby.craftsocial.models.ShopItem;
import dev.sh3lby.craftsocial.utils.InventoryMetadata;
import dev.sh3lby.craftsocial.utils.ItemNbtSupport;
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
 * GUI управления магазином с пагинацией.
 */
public class ShopManagementGui {
    private static final int DISPLAY_INVENTORY_SIZE = 54;
    private static final int ITEMS_PER_PAGE = 45;
    private static final int PREV_SLOT = 45;
    private static final int INFO_SLOT = 49;
    private static final int DELETE_SLOT = 51;
    private static final int NEXT_SLOT = 53;

    private final ShopHandler shopHandler;
    private final Plugin plugin;

    public ShopManagementGui(ShopHandler shopHandler, Plugin plugin) {
        this.shopHandler = shopHandler;
        this.plugin = plugin;
    }

    public void openManagement(Player player, Integer shopId) {
        openManagement(player, shopId, 0);
    }

    public void openManagement(Player player, Integer shopId, int page) {
        long playerId = ((dev.sh3lby.craftsocial.CraftSocialPlugin) plugin).getPlayerIdMapper().getPlayerIdSync(player);

        if (shopId == null) {
            openCreateShopGui(player);
            return;
        }

        shopHandler.invalidateShopCache(shopId);
        shopHandler.getShopLookup(shopId, playerId).thenAccept(result -> {
            Shop shop = result.getShop();
            if (shop == null) {
                MessagePalette.send(player, "Магазин не найден");
                return;
            }

            final List<ShopItem> items = shop.getItems() != null ? shop.getItems() : List.of();
            final int safePage = clampPage(page, items.size());
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                Inventory inventory = Bukkit.createInventory(null, DISPLAY_INVENTORY_SIZE, title(shop.getId(), safePage, shop.getName()));
                int start = safePage * ITEMS_PER_PAGE;
                int slot = 0;
                for (int i = start; i < items.size() && slot < ITEMS_PER_PAGE; i++) {
                    inventory.setItem(slot++, createShopItemDisplay(items.get(i)));
                }

                if (items.isEmpty()) {
                    ItemStack empty = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
                    ItemMeta emptyMeta = empty.getItemMeta();
                    if (emptyMeta != null) {
                        emptyMeta.setDisplayName(MessagePalette.paint("§fНет товаров"));
                        emptyMeta.setLore(MessagePalette.paint(List.of(
                            "§fОткройте виртуальный инвентарь магазина",
                            "§fи разложите товары по слотам"
                        )));
                        empty.setItemMeta(emptyMeta);
                    }
                    inventory.setItem(22, empty);
                }

                inventory.setItem(INFO_SLOT, infoItem(shop.getId(), items.size(), safePage));
                if (safePage > 0) {
                    inventory.setItem(PREV_SLOT, navigationItem("Предыдущая страница", safePage, items.size()));
                }
                if ((safePage + 1) * ITEMS_PER_PAGE < items.size()) {
                    inventory.setItem(NEXT_SLOT, navigationItem("Следующая страница", safePage, items.size()));
                }
                inventory.setItem(DELETE_SLOT, deleteItem());
                player.openInventory(inventory);
            });
        }).exceptionally(error -> {
            MessagePalette.send(player, "§cОшибка загрузки магазина: §f" + error.getMessage());
            return null;
        });
    }

    private void openCreateShopGui(Player player) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            Inventory inventory = Bukkit.createInventory(null, 9, "Создание магазина");
            ItemStack infoItem = new ItemStack(Material.BOOK);
            ItemMeta infoMeta = infoItem.getItemMeta();
            if (infoMeta != null) {
                infoMeta.setDisplayName(MessagePalette.paint("§cСоздание магазина"));
                infoMeta.setLore(MessagePalette.paint(List.of("§fИспользуйте команду:", "§6/shop create <название>")));
                infoItem.setItemMeta(infoMeta);
            }
            inventory.setItem(4, infoItem);
            player.openInventory(inventory);
        });
    }

    public void handleClick(Player player, int slot, ItemStack clickedItem, Integer shopId, int page, boolean rightClick, boolean shiftClick) {
        if (clickedItem == null || clickedItem.getType() == Material.AIR || shopId == null) {
            return;
        }

        if (slot == PREV_SLOT) {
            openManagement(player, shopId, Math.max(0, page - 1));
            return;
        }
        if (slot == NEXT_SLOT) {
            openManagement(player, shopId, page + 1);
            return;
        }

        long playerId = ((dev.sh3lby.craftsocial.CraftSocialPlugin) plugin).getPlayerIdMapper().getPlayerIdSync(player);
        Integer itemId = InventoryMetadata.getItemId(clickedItem, plugin);

        if (slot == DELETE_SLOT) {
            player.closeInventory();
            shopHandler.deleteShop(shopId, playerId).thenAccept(success -> {
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (success) {
                        MessagePalette.sendRaw(player, "§6Магазин удалён!");
                    } else {
                        MessagePalette.sendRaw(player, "§cОшибка удаления магазина.");
                    }
                });
            });
            return;
        }

        if (itemId == null || slot < 0 || slot >= ITEMS_PER_PAGE) {
            return;
        }

        if (shiftClick && rightClick) {
            player.closeInventory();
            shopHandler.deleteItem(shopId, itemId, playerId).thenAccept(success -> {
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (success) {
                        MessagePalette.sendRaw(player, "§6Товар удалён.");
                    } else {
                        MessagePalette.sendRaw(player, "§cОшибка удаления товара.");
                    }
                });
            });
            return;
        }

        if (shiftClick) {
            MessagePalette.sendRaw(player, "§cИзменение остатка товара из управления магазином отключено.");
            return;
        }

        if (rightClick) {
            dev.sh3lby.craftsocial.utils.PendingShopEdit.set(player.getUniqueId(), shopId, itemId, "quantity");
            player.closeInventory();
            MessagePalette.sendRaw(player, "§6Введите новый размер лота (quantity) в чат:");
            return;
        }

        dev.sh3lby.craftsocial.utils.PendingShopEdit.set(player.getUniqueId(), shopId, itemId, "price");
        player.closeInventory();
        MessagePalette.sendRaw(player, "§6Введите новую цену в чат (число):");
    }

    public static String title(int shopId, int page, String name) {
        return "Управление магазином: " + shopId + ":" + page + " " + name;
    }

    private ItemStack createShopItemDisplay(ShopItem shopItem) {
        int amount = Math.max(1, shopItem.getQuantity());
        String nbt = (shopItem.getItemNbt() != null && !shopItem.getItemNbt().isBlank()) ? shopItem.getItemNbt() : null;
        ItemStack item = ItemNbtSupport.fromNbtString(shopItem.getItemId(), nbt, amount);
        if (item == null || item.getType() == Material.AIR) {
            Material material = Material.matchMaterial(shopItem.getItemId());
            item = new ItemStack(material != null ? material : Material.BARRIER, amount);
        }
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            String readableName = toReadableItemName(item.getType(), shopItem.getItemId());
            meta.setDisplayName(MessagePalette.paint("§c" + readableName));
            List<String> lore = new ArrayList<>();
            lore.add("§fПредмет: " + readableName);
            lore.add("§fЦена за 1: §6" + shopItem.getPrice() + " вайши");
            lore.add("§fРазмер лота: §6" + shopItem.getQuantity());
            lore.add("§fОстаток: §6" + shopItem.getTotalCount());
            lore.add("");
            lore.add("§6ЛКМ - Цена");
            lore.add("§6ПКМ - Размер лота");
            lore.add("§cShift+ПКМ - Удалить");
            meta.setLore(MessagePalette.paint(lore));
            item.setItemMeta(meta);
            InventoryMetadata.setItemId(item, plugin, shopItem.getId());
        }

        return item;
    }

    private ItemStack infoItem(int shopId, int totalItems, int page) {
        ItemStack item = new ItemStack(Material.BOOK);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            int totalPages = Math.max(1, (int) Math.ceil(totalItems / (double) ITEMS_PER_PAGE));
            meta.setDisplayName(MessagePalette.paint("§cУправление магазином"));
            meta.setLore(MessagePalette.paint(List.of(
                "§fID: §6" + shopId,
                "§fТоваров: §6" + totalItems,
                "§fСтраница: §6" + (page + 1) + "/" + totalPages
            )));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack deleteItem() {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(MessagePalette.paint("§cУдалить магазин"));
            meta.setLore(MessagePalette.paint(List.of("§fНажмите для удаления")));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack navigationItem(String label, int page, int totalItems) {
        ItemStack item = new ItemStack(Material.ARROW);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            int totalPages = Math.max(1, (int) Math.ceil(totalItems / (double) ITEMS_PER_PAGE));
            meta.setDisplayName(MessagePalette.paint("§6" + label));
            meta.setLore(MessagePalette.paint(List.of("§fСтраница: §6" + (page + 1) + "/" + totalPages)));
            item.setItemMeta(meta);
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

    private int clampPage(int requestedPage, int totalItems) {
        if (requestedPage <= 0) {
            return 0;
        }
        int maxPage = Math.max(0, (int) Math.ceil(totalItems / (double) ITEMS_PER_PAGE) - 1);
        return Math.min(requestedPage, maxPage);
    }
}
