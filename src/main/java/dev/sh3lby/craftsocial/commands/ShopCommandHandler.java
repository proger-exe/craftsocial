package dev.sh3lby.craftsocial.commands;

import dev.sh3lby.craftsocial.gui.ShopGui;
import dev.sh3lby.craftsocial.gui.ShopManagementGui;
import dev.sh3lby.craftsocial.handlers.ShopHandler;
import dev.sh3lby.craftsocial.storage.ShopLocationManager;
import dev.sh3lby.craftsocial.utils.MessagePalette;
import dev.sh3lby.craftsocial.utils.PlayerIdMapper;
import org.bukkit.block.Block;
import org.bukkit.block.Barrel;
import org.bukkit.block.Chest;
import org.bukkit.block.DoubleChest;
import org.bukkit.block.ShulkerBox;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Обработчик команд для магазинов.
 */
public class ShopCommandHandler implements CommandExecutor, TabCompleter {
    private static final Logger logger = LoggerFactory.getLogger(ShopCommandHandler.class);
    
    private final ShopHandler shopHandler;
    private final ShopGui shopGui;
    private final ShopManagementGui shopManagementGui;
    private final PlayerIdMapper playerIdMapper;
    private final ShopLocationManager shopLocationManager;
    
    public ShopCommandHandler(ShopHandler shopHandler, ShopGui shopGui, ShopManagementGui shopManagementGui, 
                             PlayerIdMapper playerIdMapper, ShopLocationManager shopLocationManager) {
        this.shopHandler = shopHandler;
        this.shopGui = shopGui;
        this.shopManagementGui = shopManagementGui;
        this.playerIdMapper = playerIdMapper;
        this.shopLocationManager = shopLocationManager;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            MessagePalette.sendRaw(sender, "§cЭта команда доступна только игрокам");
            return true;
        }
        
        Player player = (Player) sender;
        long playerId = playerIdMapper.getPlayerIdSync(player);
        
        if (args.length == 0) {
            sendUsage(player);
            return true;
        }
        
        String subCommand = args[0].toLowerCase();
        
        try {
            switch (subCommand) {
                case "create":
                    if (args.length < 2) {
                        MessagePalette.sendRaw(player, "§cИспользование: §6/shop create <название>");
                        MessagePalette.sendRaw(player, "§fСмотрите на сундук или бочку при выполнении команды!");
                        return true;
                    }
                    
                    // Проверяем, смотрит ли игрок на сундук или бочку
                    Block targetBlock = player.getTargetBlock(null, 5);
                    if (targetBlock == null || 
                        (!targetBlock.getType().name().contains("CHEST") && 
                         !targetBlock.getType().name().contains("SHULKER_BOX") &&
                         !targetBlock.getType().name().contains("BARREL"))) {
                        MessagePalette.sendRaw(player, "§cВы должны смотреть на сундук или бочку!");
                        return true;
                    }
                    
                    org.bukkit.Location chestLocation = targetBlock.getLocation();

                    if (isDoubleChest(targetBlock)) {
                        MessagePalette.sendRaw(player, "§cНельзя создавать магазин на двойном сундуке.");
                        return true;
                    }
                    
                    // Проверяем, не занят ли этот контейнер другим магазином
                    Integer existingShopId = resolveShopIdFromBlock(targetBlock);
                    if (existingShopId != null) {
                        MessagePalette.sendRaw(player, "§cЭтот контейнер уже используется магазином #§f" + existingShopId);
                        return true;
                    }
                    
                    // Контейнер должен быть пустым
                    if (!isContainerEmpty(targetBlock)) {
                        MessagePalette.sendRaw(player, "§cСундук (или бочка) должен быть пустым!");
                        return true;
                    }
                    
                    String shopName = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
                    shopHandler.createShopWithLocation(shopName, playerId, chestLocation).thenAccept(shop -> {
                        if (shop != null) {
                            // Сохраняем привязку магазина к сундуку
                            shopLocationManager.setShopLocation(shop.getId(), chestLocation, playerId);
                            MessagePalette.sendRaw(player, "§6Магазин создан! ID: §f" + shop.getId());
                            MessagePalette.sendRaw(player, "§fКонтейнер привязан к магазину. Кладите товары в сундук/бочку для продажи.");
                        } else {
                            MessagePalette.sendRaw(player, "§cОшибка создания магазина");
                        }
                    });
                    return true;
                    
                case "list":
                    int page = args.length > 1 ? Integer.parseInt(args[1]) : 1;
                    shopHandler.getShops(null, playerId, page, 20).thenAccept(shops -> {
                        if (shops != null && !shops.isEmpty()) {
                            MessagePalette.sendRaw(player, "§c=== Магазины (стр. §6" + page + "§c) ===");
                            for (dev.sh3lby.craftsocial.models.Shop shop : shops) {
                                MessagePalette.sendRaw(player, "§fID: §6" + shop.getId() + " §f- §c" + shop.getName());
                            }
                        } else {
                            MessagePalette.sendRaw(player, "§fМагазины не найдены");
                        }
                    });
                    return true;
                    
                case "info":
                    if (args.length < 2) {
                        MessagePalette.sendRaw(player, "§cИспользование: §6/shop info <shopId>");
                        return true;
                    }
                    int shopId = Integer.parseInt(args[1]);
                    shopHandler.invalidateShopCache(shopId);
                    shopHandler.getShopLookup(shopId, playerId).thenAccept(result -> {
                        dev.sh3lby.craftsocial.models.Shop shop = result.getShop();
                        if (shop != null) {
                            MessagePalette.sendRaw(player, "§c=== Магазин: §6" + shop.getName() + " §c===");
                            MessagePalette.sendRaw(player, "§fID: §6" + shop.getId());
                            MessagePalette.sendRaw(player, "§fВладелец: §6" + shop.getOwnerId());
                            MessagePalette.sendRaw(player, "§fТоваров: §6" + (shop.getItems() != null ? shop.getItems().size() : 0));
                        } else {
                            MessagePalette.sendRaw(player, "§cМагазин не найден");
                        }
                    });
                    return true;
                    
                case "open":
                    int openShopId = resolveShopId(player, args, 1);
                    if (openShopId == -1) {
                        MessagePalette.sendRaw(player, "§cУкажите ID магазина или смотрите на сундук магазина: §6/shop open [shopId]");
                        return true;
                    }
                    shopGui.openShop(player, openShopId);
                    return true;
                    
                case "manage":
                    Integer manageShopId = args.length > 1 ? Integer.parseInt(args[1]) : resolveShopIdFromLook(player);
                    if (manageShopId == null) {
                        MessagePalette.sendRaw(player, "§cСмотрите на сундук магазина или укажите ID: §6/shop manage <shopId>");
                        return true;
                    }
                    shopManagementGui.openManagement(player, manageShopId);
                    return true;
                    
                case "delete":
                    if (args.length < 2) {
                        MessagePalette.sendRaw(player, "§cИспользование: §6/shop delete <shopId>");
                        return true;
                    }
                    int deleteShopId = Integer.parseInt(args[1]);
                    shopHandler.deleteShop(deleteShopId, playerId).thenAccept(success -> {
                        if (success) {
                            MessagePalette.sendRaw(player, "§6Магазин удален");
                        } else {
                            MessagePalette.sendRaw(player, "§cОшибка удаления магазина");
                        }
                    });
                    return true;
                    
                case "additem":
                    if (args.length < 4) {
                        MessagePalette.sendRaw(player, "§cИспользование: §6/shop additem <shopId> <item> <price> [lot_quantity] [total_count]");
                        return true;
                    }
                    int addItemShopId = Integer.parseInt(args[1]);
                    String itemId = args[2];
                    long price = Long.parseLong(args[3]);
                    if (price < 1) {
                        MessagePalette.sendRaw(player, "§cМинимальная цена: §f1 вайши.");
                        return true;
                    }
                    int quantity = args.length > 4 ? Integer.parseInt(args[4]) : 1;
                    int totalCount = args.length > 5 ? Integer.parseInt(args[5]) : quantity;
                    if (quantity <= 0 || totalCount < 0) {
                        MessagePalette.sendRaw(player, "§cНекорректные значения: §flot_quantity > 0, total_count >= 0");
                        return true;
                    }
                    shopHandler.addItem(addItemShopId, itemId, price, quantity, totalCount, playerId).thenAccept(item -> {
                        if (item != null) {
                            MessagePalette.sendRaw(player, "§6Товар добавлен! ID: §f" + item.getId());
                        } else {
                            MessagePalette.sendRaw(player, "§cОшибка добавления товара");
                        }
                    });
                    return true;
                    
                case "edititem":
                    if (args.length < 4) {
                        MessagePalette.sendRaw(player, "§cИспользование: §6/shop edititem <shopId> <itemId> <price> [lot_quantity] [total_count]");
                        return true;
                    }
                    int editItemShopId = Integer.parseInt(args[1]);
                    int editItemId = Integer.parseInt(args[2]);
                    long editPrice = Long.parseLong(args[3]);
                    if (editPrice < 1) {
                        MessagePalette.sendRaw(player, "§cМинимальная цена: §f1 вайши.");
                        return true;
                    }
                    Integer editQuantity = args.length > 4 ? Integer.parseInt(args[4]) : null;
                    Integer editTotalCount = args.length > 5 ? Integer.parseInt(args[5]) : null;
                    if (editQuantity != null && editQuantity <= 0) {
                        MessagePalette.sendRaw(player, "§clot_quantity должно быть > 0");
                        return true;
                    }
                    if (editTotalCount != null && editTotalCount < 0) {
                        MessagePalette.sendRaw(player, "§ctotal_count должно быть >= 0");
                        return true;
                    }
                    shopHandler.updateItem(editItemShopId, editItemId, editPrice, editQuantity, editTotalCount, playerId).thenAccept(success -> {
                        if (success) {
                            MessagePalette.sendRaw(player, "§6Товар обновлен");
                        } else {
                            MessagePalette.sendRaw(player, "§cОшибка обновления товара");
                        }
                    });
                    return true;
                    
                case "removeitem":
                    if (args.length < 3) {
                        MessagePalette.sendRaw(player, "§cИспользование: §6/shop removeitem <shopId> <itemId>");
                        return true;
                    }
                    int removeItemShopId = Integer.parseInt(args[1]);
                    int removeItemId = Integer.parseInt(args[2]);
                    shopHandler.deleteItem(removeItemShopId, removeItemId, playerId).thenAccept(success -> {
                        if (success) {
                            MessagePalette.sendRaw(player, "§6Товар удален");
                        } else {
                            MessagePalette.sendRaw(player, "§cОшибка удаления товара");
                        }
                    });
                    return true;
                    
                default:
                    sendUsage(player);
                    return true;
            }
        } catch (NumberFormatException e) {
            MessagePalette.sendRaw(player, "§cНеверный формат числа");
            return true;
        } catch (Exception e) {
            logger.error("Error executing shop command", e);
            MessagePalette.sendRaw(player, "§cОшибка выполнения команды: §f" + e.getMessage());
            return true;
        }
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("create", "list", "info", "open", "manage", "delete", "additem", "edititem", "removeitem")
                .stream()
                .filter(cmd -> cmd.startsWith(args[0].toLowerCase()))
                .collect(Collectors.toList());
        }
        
        if (args.length == 2) {
            String subCommand = args[0].toLowerCase();
            if (subCommand.equals("list")) {
                return Arrays.asList("1", "2", "3");
            }
        }
        
        return new ArrayList<>();
    }
    
    private void sendUsage(Player player) {
        MessagePalette.sendRaw(player, "§c=== Команды магазинов ===");
        MessagePalette.sendRaw(player, "§6/shop create <название> §f- Создать магазин");
        MessagePalette.sendRaw(player, "§6/shop list [page] §f- Список магазинов");
        MessagePalette.sendRaw(player, "§6/shop info <shopId> §f- Информация о магазине");
        MessagePalette.sendRaw(player, "§6/shop open <shopId> §f- Открыть магазин");
        MessagePalette.sendRaw(player, "§6/shop manage [shopId] §f- Управление магазином");
        MessagePalette.sendRaw(player, "§6/shop delete <shopId> §f- Удалить магазин");
        MessagePalette.sendRaw(player, "§6/shop additem <shopId> <item> <price> [lot] [total] §f- Добавить позицию");
        MessagePalette.sendRaw(player, "§6/shop edititem <shopId> <itemId> <price> [lot] [total] §f- Обновить позицию");
        MessagePalette.sendRaw(player, "§fquantity = размер лота, total_count = остаток.");
    }

    /** Разрешает shopId: из аргумента или из блока, на который смотрит игрок. */
    private int resolveShopId(Player player, String[] args, int argIndex) {
        if (args.length > argIndex) {
            try {
                return Integer.parseInt(args[argIndex]);
            } catch (NumberFormatException e) {
                return -1;
            }
        }
        Integer fromLook = resolveShopIdFromLook(player);
        return fromLook != null ? fromLook : -1;
    }

    /** ID магазина по контейнеру (сундук/бочка), на который смотрит игрок. */
    private Integer resolveShopIdFromLook(Player player) {
        Block target = player.getTargetBlock(null, 5);
        if (target == null || (!target.getType().name().contains("CHEST") && !target.getType().name().contains("SHULKER_BOX") && !target.getType().name().contains("BARREL"))) {
            return null;
        }
        return resolveShopIdFromBlock(target);
    }

    /** Проверяет, пуст ли контейнер (сундук, бочка, шалкер-бокс). */
    private boolean isContainerEmpty(Block block) {
        org.bukkit.block.BlockState state = block.getState();
        Inventory inv = null;
        if (state instanceof Chest) {
            inv = ((Chest) state).getInventory();
        } else if (state instanceof Barrel) {
            inv = ((Barrel) state).getInventory();
        } else if (state instanceof ShulkerBox) {
            inv = ((ShulkerBox) state).getInventory();
        }
        if (inv == null) {
            return true;
        }
        for (org.bukkit.inventory.ItemStack item : inv.getContents()) {
            if (item != null && !item.getType().isAir() && !item.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private Integer resolveShopIdFromBlock(Block block) {
        Integer direct = shopLocationManager.getShopIdByLocation(block.getLocation());
        if (direct != null) {
            return direct;
        }

        org.bukkit.block.BlockState state = block.getState();
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

    private boolean isDoubleChest(Block block) {
        org.bukkit.block.BlockState state = block.getState();
        return state instanceof Chest && ((Chest) state).getInventory().getHolder() instanceof DoubleChest;
    }
}
