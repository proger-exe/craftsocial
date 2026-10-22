package dev.sh3lby.craftsocial.listeners;

import dev.sh3lby.craftsocial.CraftSocialPlugin;
import dev.sh3lby.craftsocial.handlers.ShopHandler;
import dev.sh3lby.craftsocial.models.Shop;
import dev.sh3lby.craftsocial.storage.ShopInventoryManager;
import dev.sh3lby.craftsocial.storage.ShopLocationManager;
import dev.sh3lby.craftsocial.utils.MessagePalette;
import dev.sh3lby.craftsocial.utils.PlayerIdMapper;
import dev.sh3lby.craftsocial.utils.ItemNbtSupport;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.block.Barrel;
import org.bukkit.block.DoubleChest;
import org.bukkit.block.Hopper;
import org.bukkit.block.ShulkerBox;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.entity.minecart.HopperMinecart;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.vehicle.VehicleMoveEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.block.BlockFace;
import org.bukkit.Location;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;

/**
 * Listener для работы с сундуками магазинов.
 * Обрабатывает открытие магазина через сундук, пополнение товаров и полный запрет поломки сундука магазина.
 */
public class ShopChestListener implements Listener {
    private static final Logger logger = LoggerFactory.getLogger(ShopChestListener.class);
    
    private final CraftSocialPlugin plugin;
    private final ShopHandler shopHandler;
    private final ShopLocationManager shopLocationManager;
    private final ShopInventoryManager shopInventoryManager;
    private final PlayerIdMapper playerIdMapper;
    
    public ShopChestListener(CraftSocialPlugin plugin, ShopHandler shopHandler, 
                            ShopLocationManager shopLocationManager, PlayerIdMapper playerIdMapper) {
        this.plugin = plugin;
        this.shopHandler = shopHandler;
        this.shopLocationManager = shopLocationManager;
        this.shopInventoryManager = plugin.getShopInventoryManager();
        this.playerIdMapper = playerIdMapper;
    }
    
    /**
     * Обрабатывает клик по сундуку магазина.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onChestClick(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        
        Block block = event.getClickedBlock();
        if (block == null || (!block.getType().name().contains("CHEST") && 
                              !block.getType().name().contains("SHULKER_BOX") &&
                              !block.getType().name().contains("BARREL"))) {
            return;
        }
        
        Integer shopId = resolveShopId(block);
        
        if (shopId == null) {
            return;
        }

        ItemStack usedItem = event.getItem();
        if (usedItem != null && (usedItem.getType() == Material.ITEM_FRAME || usedItem.getType() == Material.GLOW_ITEM_FRAME)) {
            return;
        }
        
        event.setCancelled(true);
        
        Player player = event.getPlayer();
        long playerId = playerIdMapper.getPlayerIdSync(player);
        
        // Всегда открываем меню покупки (GUI магазина).
        plugin.getServer().getScheduler().runTask(plugin, () -> plugin.getShopGui().openShop(player, shopId));
    }
    
    /**
     * Полный запрет на поломку сундука/бочки магазина (и для владельца, и для остальных).
     * Отмена выполняется синхронно, чтобы блок не успел сломаться до ответа API.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onShopChestBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (block == null || (!block.getType().name().contains("CHEST") &&
                !block.getType().name().contains("SHULKER_BOX") &&
                !block.getType().name().contains("BARREL"))) {
            return;
        }
        Integer shopId = resolveShopId(block);
        if (shopId == null) {
            return;
        }
        event.setCancelled(true);
        if (event.getPlayer() instanceof Player) {
            MessagePalette.sendRaw((Player) event.getPlayer(), "§cСундук магазина ломать нельзя.");
        }
    }
    
    /**
     * Запрет объединения сундука магазина в двойной: нельзя ставить обычный сундук рядом с сундуком магазина.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Block block = event.getBlock();
        Material type = block.getType();
        if (type == Material.HOPPER) {
            if (isHopperConnectedToShop(block)) {
                event.setCancelled(true);
                if (event.getPlayer() instanceof Player) {
                    MessagePalette.sendRaw((Player) event.getPlayer(), "§cНельзя ставить воронку так, чтобы она взаимодействовала с магазином.");
                }
            }
            return;
        }

        if (type != Material.CHEST && type != Material.TRAPPED_CHEST) {
            return;
        }

        if (isDoubleChest(block)) {
            Integer expandedShopId = findNeighborShopId(block);
            if (expandedShopId != null) {
                Long ownerId = shopLocationManager.getShopOwnerId(expandedShopId);
                long playerId = playerIdMapper.getPlayerIdSync(event.getPlayer());
                if (ownerId != null && ownerId == playerId) {
                    shopInventoryManager.getInventory(expandedShopId, ShopInventoryManager.DOUBLE_CHEST_INVENTORY_SIZE);
                    return;
                }
                event.setCancelled(true);
                if (event.getPlayer() instanceof Player) {
                    MessagePalette.sendRaw((Player) event.getPlayer(), "§cНельзя создавать двойной сундук рядом с сундуком магазина.");
                }
                return;
            }
        }

        for (BlockFace face : new BlockFace[] { BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST }) {
            Block neighbor = block.getRelative(face);
            if (neighbor.getType() != Material.CHEST && neighbor.getType() != Material.TRAPPED_CHEST) {
                continue;
            }
            Integer shopId = resolveShopId(neighbor);
            if (shopId != null) {
                Long ownerId = shopLocationManager.getShopOwnerId(shopId);
                long playerId = playerIdMapper.getPlayerIdSync(event.getPlayer());
                if (ownerId != null && ownerId == playerId) {
                    continue;
                }
                event.setCancelled(true);
                if (event.getPlayer() instanceof Player) {
                    MessagePalette.sendRaw((Player) event.getPlayer(), "§cНельзя ставить сундук рядом с сундуком магазина.");
                }
                return;
            }
        }
    }
    
    /**
     * Запрет вытягивания товара из сундука/бочки магазина воронкой.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryMoveItem(InventoryMoveItemEvent event) {
        if (isProtectedInventory(event.getSource()) || isProtectedInventory(event.getDestination())) {
            event.setCancelled(true);
            if (logger.isDebugEnabled()) {
                logger.debug("Blocked inventory move: sourceHolder={}, destinationHolder={}, item={}",
                    describeHolder(event.getSource()), describeHolder(event.getDestination()), event.getItem() != null ? event.getItem().getType() : "null");
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onVehicleMove(VehicleMoveEvent event) {
        if (!(event.getVehicle() instanceof HopperMinecart hopperMinecart)) {
            return;
        }

        if (!isHopperMinecartUnderShop(event.getTo()) || isHopperMinecartUnderShop(event.getFrom())) {
            return;
        }

        hopperMinecart.teleport(event.getFrom());
    }
    
    /**
     * Взрыв сущности (TNT, крипер и т.д.): убираем сундуки/бочки магазина из списка разрушаемых блоков.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(b -> isShopChestOrBarrel(b));
    }
    
    /**
     * Взрыв блока (например TNT): убираем сундуки/бочки магазина из списка разрушаемых блоков.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(b -> isShopChestOrBarrel(b));
    }
    
    /**
     * Поршень не может сдвигать сундук/бочку магазина.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        for (Block block : event.getBlocks()) {
            if (isShopChestOrBarrel(block)) {
                event.setCancelled(true);
                return;
            }
        }
    }
    
    /**
     * Огонь не может сжечь сундук/бочку магазина.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBurn(BlockBurnEvent event) {
        if (isShopChestOrBarrel(event.getBlock())) {
            event.setCancelled(true);
        }
    }
    
    private boolean isShopChestOrBarrel(Block block) {
        if (block == null) return false;
        if (!isSupportedContainer(block.getType())) {
            return false;
        }
        return resolveShopId(block) != null;
    }
    
    /**
     * Обрабатывает закрытие сундука магазина (для владельца).
     * Синхронизирует товары из сундука с магазином в CRM.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onChestClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) {
            return;
        }
        
        Player player = (Player) event.getPlayer();
        Inventory inventory = event.getInventory();
        Integer shopId = resolveShopId(inventory);
        if (shopId == null) {
            return;
        }
        long playerId = playerIdMapper.getPlayerIdSync(player);
        ItemStack[] snapshot = cloneContents(inventory.getContents());
        clearPhysicalInventory(inventory);
        
        // Проверяем, является ли игрок владельцем
        shopHandler.invalidateShopCache(shopId);
        shopHandler.getShopLookup(shopId, playerId).thenAccept(result -> {
            Shop shop = result.getShop();
            if (shop == null || shop.getOwnerId() != playerId) {
                return;
            }

            int desiredSize = desiredInventorySize(inventory);
            shopInventoryManager.setInventory(shopId, snapshot, desiredSize);
            shopHandler.enqueueChestSync(shopId, () -> shopHandler.syncInventorySnapshot(shopId, playerId, snapshot));
        });
    }

    /**
     * Каждое изменение инвентаря сундука магазина владельцем сразу синхронизируется на сервер.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onShopChestClick(InventoryClickEvent event) {
        // Намеренно не синхронизируем на каждый клик: источник истины для редактирования владельцем — физический контейнер,
        // а запись в API выполняется одним full-sync при закрытии, чтобы исключить гонки и потерю предметов.
    }

    /**
     * Drag тоже меняет инвентарь, поэтому также синхронизируем.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onShopChestDrag(InventoryDragEvent event) {
        // См. onShopChestClick: синхронизация делается на закрытии инвентаря.
    }
    
    private boolean isSupportedContainer(Material type) {
        return type.name().contains("CHEST") || type.name().contains("SHULKER_BOX") || type.name().contains("BARREL");
    }

    private boolean isDoubleChest(Block block) {
        BlockState state = block.getState();
        return state instanceof Chest && ((Chest) state).getInventory().getHolder() instanceof DoubleChest;
    }

    private boolean isShopInventory(Inventory inventory) {
        return resolveShopId(inventory) != null;
    }

    private boolean isProtectedInventory(Inventory inventory) {
        return isShopInventory(inventory) || isProtectedTransportInventory(inventory);
    }

    private boolean isProtectedTransportInventory(Inventory inventory) {
        if (inventory == null) {
            return false;
        }

        InventoryHolder holder = inventory.getHolder();
        if (holder instanceof Hopper hopper) {
            return isHopperConnectedToShop(hopper.getBlock());
        }
        if (holder instanceof HopperMinecart hopperMinecart) {
            return isHopperMinecartUnderShop(hopperMinecart.getLocation());
        }
        return false;
    }

    private Integer resolveShopId(Inventory inventory) {
        if (inventory == null) {
            return null;
        }

        InventoryHolder holder = inventory.getHolder();
        if (holder instanceof BlockState) {
            return resolveShopId(((BlockState) holder).getBlock());
        }
        if (holder instanceof DoubleChest) {
            DoubleChest doubleChest = (DoubleChest) holder;
            Integer left = getShopIdFromHolder(doubleChest.getLeftSide());
            return left != null ? left : getShopIdFromHolder(doubleChest.getRightSide());
        }
        return null;
    }

    private Integer resolveShopId(Block block) {
        if (block == null || !isSupportedContainer(block.getType())) {
            return null;
        }

        Integer direct = shopLocationManager.getShopIdByLocation(block.getLocation());
        if (direct != null) {
            return direct;
        }

        BlockState state = block.getState();
        if (!(state instanceof Chest)) {
            return null;
        }

        Inventory holderInventory = ((Chest) state).getInventory();
        if (!(holderInventory.getHolder() instanceof DoubleChest)) {
            return null;
        }

        DoubleChest doubleChest = (DoubleChest) holderInventory.getHolder();
        Integer left = getShopIdFromHolder(doubleChest.getLeftSide());
        return left != null ? left : getShopIdFromHolder(doubleChest.getRightSide());
    }

    private Integer getShopIdFromHolder(Object holder) {
        if (!(holder instanceof Chest)) {
            return null;
        }
        return shopLocationManager.getShopIdByLocation(((Chest) holder).getLocation());
    }

    private boolean isHopperConnectedToShop(Block hopperBlock) {
        if (hopperBlock == null || hopperBlock.getType() != Material.HOPPER) {
            return false;
        }

        if (isShopChestOrBarrel(hopperBlock.getRelative(BlockFace.UP))) {
            return true;
        }

        BlockData data = hopperBlock.getBlockData();
        if (!(data instanceof org.bukkit.block.data.type.Hopper hopperData)) {
            return false;
        }

        return isShopChestOrBarrel(hopperBlock.getRelative(hopperData.getFacing()));
    }

    private boolean isHopperMinecartUnderShop(Location location) {
        if (location == null || location.getWorld() == null) {
            return false;
        }

        Block baseBlock = location.getBlock();
        return isShopChestOrBarrel(baseBlock.getRelative(BlockFace.UP))
            || isShopChestOrBarrel(baseBlock.getRelative(BlockFace.UP, 2));
    }

    private String describeHolder(Inventory inventory) {
        if (inventory == null || inventory.getHolder() == null) {
            return "null";
        }
        return inventory.getHolder().getClass().getSimpleName();
    }

    private ItemStack[] cloneContents(ItemStack[] contents) {
        ItemStack[] snapshot = new ItemStack[contents.length];
        for (int i = 0; i < contents.length; i++) {
            snapshot[i] = contents[i] != null ? contents[i].clone() : null;
        }
        return snapshot;
    }

    private Integer findNeighborShopId(Block block) {
        for (BlockFace face : new BlockFace[] { BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST }) {
            Integer shopId = resolveShopId(block.getRelative(face));
            if (shopId != null) {
                return shopId;
            }
        }
        return null;
    }

    private int desiredInventorySize(Inventory inventory) {
        if (inventory != null && inventory.getHolder() instanceof DoubleChest) {
            return ShopInventoryManager.DOUBLE_CHEST_INVENTORY_SIZE;
        }
        return ShopInventoryManager.DEFAULT_INVENTORY_SIZE;
    }

    private void clearPhysicalInventory(Inventory inventory) {
        plugin.getServer().getScheduler().runTask(plugin, () -> inventory.clear());
    }
}
