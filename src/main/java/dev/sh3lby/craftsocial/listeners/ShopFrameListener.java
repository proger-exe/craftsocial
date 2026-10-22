package dev.sh3lby.craftsocial.listeners;

import dev.sh3lby.craftsocial.handlers.ShopHandler;
import dev.sh3lby.craftsocial.storage.ShopLocationManager;
import dev.sh3lby.craftsocial.utils.MessagePalette;
import dev.sh3lby.craftsocial.utils.PlayerIdMapper;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.block.DoubleChest;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public class ShopFrameListener implements Listener {
    private final ShopHandler shopHandler;
    private final ShopLocationManager shopLocationManager;
    private final PlayerIdMapper playerIdMapper;

    public ShopFrameListener(ShopHandler shopHandler, ShopLocationManager shopLocationManager, PlayerIdMapper playerIdMapper) {
        this.shopHandler = shopHandler;
        this.shopLocationManager = shopLocationManager;
        this.playerIdMapper = playerIdMapper;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHangingPlace(HangingPlaceEvent event) {
        if (!(event.getEntity() instanceof ItemFrame) || !(event.getPlayer() instanceof Player)) {
            return;
        }

        Integer shopId = resolveShopId(event.getBlock());
        if (shopId == null) {
            return;
        }

        Player player = event.getPlayer();
        if (!isOwner(player, shopId)) {
            event.setCancelled(true);
            MessagePalette.sendRaw(player, "§cТолько владелец магазина может ставить рамки на витрину.");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof ItemFrame)) {
            return;
        }

        Integer shopId = resolveShopId((ItemFrame) event.getRightClicked());
        if (shopId == null) {
            return;
        }

        if (!isOwner(event.getPlayer(), shopId)) {
            event.setCancelled(true);
            MessagePalette.sendRaw(event.getPlayer(), "§cЧужую рамку магазина менять нельзя.");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof ItemFrame)) {
            return;
        }

        Player player = resolvePlayer(event.getDamager());
        if (player == null) {
            return;
        }

        Integer shopId = resolveShopId((ItemFrame) event.getEntity());
        if (shopId == null) {
            return;
        }

        if (!isOwner(player, shopId)) {
            event.setCancelled(true);
            MessagePalette.sendRaw(player, "§cЧужую рамку магазина ломать нельзя.");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHangingBreakByEntity(HangingBreakByEntityEvent event) {
        if (!(event.getEntity() instanceof ItemFrame)) {
            return;
        }

        Player player = resolvePlayer(event.getRemover());
        if (player == null) {
            event.setCancelled(true);
            return;
        }

        Integer shopId = resolveShopId((ItemFrame) event.getEntity());
        if (shopId == null) {
            return;
        }

        if (!isOwner(player, shopId)) {
            event.setCancelled(true);
            MessagePalette.sendRaw(player, "§cЧужую рамку магазина ломать нельзя.");
        }
    }

    private Player resolvePlayer(Entity entity) {
        if (entity instanceof Player) {
            return (Player) entity;
        }
        if (entity instanceof org.bukkit.entity.Projectile
            && ((org.bukkit.entity.Projectile) entity).getShooter() instanceof Player) {
            return (Player) ((org.bukkit.entity.Projectile) entity).getShooter();
        }
        return null;
    }

    private boolean isOwner(Player player, int shopId) {
        Long ownerId = shopLocationManager.getShopOwnerId(shopId);
        if (ownerId == null) {
            dev.sh3lby.craftsocial.models.Shop cached = shopHandler.getCachedShop(shopId);
            if (cached != null) {
                ownerId = cached.getOwnerId();
                shopLocationManager.setShopOwner(shopId, ownerId);
            }
        }

        if (ownerId == null) {
            return false;
        }

        return ownerId == playerIdMapper.getPlayerIdSync(player);
    }

    private Integer resolveShopId(ItemFrame frame) {
        Block attached = frame.getLocation().getBlock().getRelative(frame.getAttachedFace());
        return resolveShopId(attached);
    }

    private Integer resolveShopId(Block block) {
        if (block == null) {
            return null;
        }

        Material type = block.getType();
        if (!type.name().contains("CHEST") && !type.name().contains("SHULKER_BOX") && !type.name().contains("BARREL")) {
            return null;
        }

        Integer direct = shopLocationManager.getShopIdByLocation(block.getLocation());
        if (direct != null) {
            return direct;
        }

        if (!(block.getState() instanceof Chest)) {
            return null;
        }

        Inventory inventory = ((Chest) block.getState()).getInventory();
        if (!(inventory.getHolder() instanceof DoubleChest)) {
            return null;
        }

        DoubleChest doubleChest = (DoubleChest) inventory.getHolder();
        Integer left = getShopIdFromHolder(doubleChest.getLeftSide());
        return left != null ? left : getShopIdFromHolder(doubleChest.getRightSide());
    }

    private Integer getShopIdFromHolder(Object holder) {
        if (!(holder instanceof Chest)) {
            return null;
        }
        return shopLocationManager.getShopIdByLocation(((Chest) holder).getLocation());
    }
}
