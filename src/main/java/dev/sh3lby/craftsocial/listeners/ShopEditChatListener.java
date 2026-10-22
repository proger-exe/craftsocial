package dev.sh3lby.craftsocial.listeners;

import dev.sh3lby.craftsocial.CraftSocialPlugin;
import dev.sh3lby.craftsocial.gui.ShopManagementGui;
import dev.sh3lby.craftsocial.handlers.ShopHandler;
import dev.sh3lby.craftsocial.utils.PendingShopEdit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Обрабатывает ввод в чат для настройки цены/количества товара в manage.
 */
public class ShopEditChatListener implements Listener {
    private final CraftSocialPlugin plugin;

    public ShopEditChatListener(CraftSocialPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent event) {
        PendingShopEdit edit = PendingShopEdit.getAndRemove(event.getPlayer().getUniqueId());
        if (edit == null) {
            return;
        }
        event.setCancelled(true);
        String msg = event.getMessage().trim();
        Player player = event.getPlayer();
        long playerId = plugin.getPlayerIdMapper().getPlayerIdSync(player);
        int shopId = edit.getShopId();
        int itemId = edit.getItemId();
        String field = edit.getField();

        long num;
        try {
            num = Long.parseLong(msg);
            if (num < 0) {
                player.sendMessage("§cВведите неотрицательное число.");
                return;
            }
        } catch (NumberFormatException e) {
            player.sendMessage("§cВведите число.");
            return;
        }
        if ("price".equals(field) && num < ShopHandler.MIN_SHOP_PRICE) {
            player.sendMessage("§cМинимальная цена: 1 вайши.");
            return;
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                plugin.getShopHandler().invalidateShopCache(shopId);
                plugin.getShopHandler().getShopLookup(shopId, playerId).thenAccept(result -> {
                    dev.sh3lby.craftsocial.models.Shop shop = result.getShop();
                    if (shop == null || shop.getItems() == null || shop.getItems().isEmpty()) {
                        new BukkitRunnable() {
                            @Override
                            public void run() {
                                player.sendMessage("§cТовар не найден.");
                            }
                        }.runTask(plugin);
                        return;
                    }
                    dev.sh3lby.craftsocial.models.ShopItem item = shop.getItems().stream()
                            .filter(i -> i.getId() == itemId).findFirst().orElse(null);
                    if (item == null) {
                        new BukkitRunnable() {
                            @Override
                            public void run() {
                                player.sendMessage("§cТовар не найден.");
                            }
                        }.runTask(plugin);
                        return;
                    }
                    if ("price".equals(field)) {
                        plugin.getShopHandler().updateItem(shopId, itemId, num, item.getQuantity(), item.getTotalCount(), playerId).thenAccept(success -> {
                            new BukkitRunnable() {
                                @Override
                                public void run() {
                                    if (success) {
                                        player.sendMessage("§aЦена установлена: " + num);
                                        new ShopManagementGui(plugin.getShopHandler(), plugin).openManagement(player, shopId);
                                    } else player.sendMessage("§cОшибка обновления цены.");
                                }
                            }.runTask(plugin);
                        });
                    } else if ("quantity".equals(field)) {
                        int total = item.getTotalCount();
                        if (total <= 0) {
                            new BukkitRunnable() {
                                @Override
                                public void run() {
                                    player.sendMessage("§cОстаток товара 0, сначала пополните магазин.");
                                }
                            }.runTask(plugin);
                            return;
                        }
                        int maxQty = total;
                        int qty = (int) Math.min(num, maxQty);
                        if (qty <= 0) {
                            new BukkitRunnable() {
                                @Override
                                public void run() {
                                    player.sendMessage("§cРазмер лота должен быть не менее 1 и не больше остатка (" + total + ").");
                                }
                            }.runTask(plugin);
                            return;
                        }
                        int finalQty = qty;
                        plugin.getShopHandler().updateItem(shopId, itemId, item.getPrice(), finalQty, item.getTotalCount(), playerId).thenAccept(success -> {
                            new BukkitRunnable() {
                                @Override
                                public void run() {
                                    if (success) {
                                        if (finalQty < num) {
                                            player.sendMessage("§eРазмер лота ограничен остатком: " + finalQty + " (из " + total + ").");
                                        } else {
                                            player.sendMessage("§aКоличество установлено: " + finalQty);
                                        }
                                        new ShopManagementGui(plugin.getShopHandler(), plugin).openManagement(player, shopId);
                                    } else {
                                        player.sendMessage("§cОшибка обновления количества.");
                                    }
                                }
                            }.runTask(plugin);
                        });
                    } else if ("total_count".equals(field)) {
                        new BukkitRunnable() {
                            @Override
                            public void run() {
                                player.sendMessage("§cИзменение остатка товара отключено.");
                            }
                        }.runTask(plugin);
                    } else {
                        new BukkitRunnable() {
                            @Override
                            public void run() {
                                player.sendMessage("§cНеизвестное поле редактирования.");
                            }
                        }.runTask(plugin);
                    }
                });
            }
        }.runTaskAsynchronously(plugin);
    }
}
