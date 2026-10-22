package dev.sh3lby.craftsocial.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.sh3lby.craftsocial.models.Shop;
import dev.sh3lby.craftsocial.models.ShopItem;
import dev.sh3lby.craftsocial.utils.ItemNbtSupport;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Локальное JSON-хранилище инвентарей магазинов.
 * Источник истины для режима редактирования владельцем — файл, а не физический NBT контейнера.
 */
public class ShopInventoryManager {
    private static final Logger logger = LoggerFactory.getLogger(ShopInventoryManager.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    public static final int DEFAULT_INVENTORY_SIZE = 54;
    public static final int DOUBLE_CHEST_INVENTORY_SIZE = 108;

    private final Path inventoriesDir;
    private final Map<Integer, ItemStack[]> inventories = new ConcurrentHashMap<>();

    public ShopInventoryManager(Plugin plugin) {
        this.inventoriesDir = plugin.getDataFolder().toPath().resolve("shop-inventories");
        try {
            Files.createDirectories(inventoriesDir);
        } catch (IOException e) {
            logger.error("Failed to create shop inventories directory", e);
        }
    }

    /**
     * Загружает инвентарь магазина из файла или выполняет единоразовый bootstrap из API shop.items.
     */
    public ItemStack[] loadOrBootstrapFromApi(int shopId, Shop shop, int desiredSize) {
        int normalizedSize = normalizeSize(desiredSize);
        if (inventories.containsKey(shopId)) {
            ItemStack[] resized = resizeInventory(inventories.get(shopId), normalizedSize);
            inventories.put(shopId, resized);
            return cloneInventory(resized);
        }

        Path file = inventoriesDir.resolve(shopId + ".json");
        if (Files.exists(file)) {
            ItemStack[] loaded = loadFromFile(shopId, file);
            ItemStack[] resized = resizeInventory(loaded, normalizedSize);
            inventories.put(shopId, resized);
            if (resized.length != loaded.length) {
                saveInventory(shopId);
            }
            return cloneInventory(resized);
        }

        ItemStack[] bootstrapped = fromShopItems(shopId, shop, normalizedSize);
        if (bootstrapped == null) {
            logger.warn("Failed to bootstrap shop inventory {} from API items", shopId);
            return null;
        }

        inventories.put(shopId, cloneInventory(bootstrapped));
        saveInventory(shopId);
        return cloneInventory(bootstrapped);
    }

    public ItemStack[] getInventory(int shopId, int desiredSize) {
        int normalizedSize = normalizeSize(desiredSize);
        ItemStack[] inventory = inventories.get(shopId);
        if (inventory == null) {
            return new ItemStack[normalizedSize];
        }
        ItemStack[] resized = resizeInventory(inventory, normalizedSize);
        if (resized.length != inventory.length) {
            inventories.put(shopId, resized);
        }
        return cloneInventory(resized);
    }

    public void setInventory(int shopId, ItemStack[] inventory, int desiredSize) {
        inventories.put(shopId, normalizeInventory(inventory, desiredSize));
        saveInventory(shopId);
    }

    public int getInventorySize(int shopId) {
        ItemStack[] inventory = inventories.get(shopId);
        return inventory != null ? inventory.length : DEFAULT_INVENTORY_SIZE;
    }

    /**
     * Уменьшает локальный JSON-инвентарь после успешной покупки.
     * Если файл для магазина еще не создавался, изменение не требуется: bootstrap позже придет из API.
     */
    public void consumePurchasedItems(int shopId, String itemId, String itemNbt, int quantity) {
        if (quantity <= 0) {
            return;
        }

        ItemStack[] inventory = getMutableInventory(shopId);
        if (inventory == null) {
            return;
        }

        String targetKey = ItemNbtSupport.slotKey(itemId, itemNbt);
        int remaining = quantity;
        for (int slot = 0; slot < inventory.length && remaining > 0; slot++) {
            ItemStack stack = inventory[slot];
            if (stack == null || stack.getType() == Material.AIR || stack.isEmpty()) {
                continue;
            }

            String slotKey = ItemNbtSupport.slotKey(stack.getType().getKey().toString(), ItemNbtSupport.toNbtString(stack));
            if (!targetKey.equals(slotKey)) {
                continue;
            }

            int stackAmount = stack.getAmount();
            if (stackAmount <= remaining) {
                inventory[slot] = null;
                remaining -= stackAmount;
            } else {
                ItemStack updated = stack.clone();
                updated.setAmount(stackAmount - remaining);
                inventory[slot] = updated;
                remaining = 0;
            }
        }

        if (remaining > 0) {
            logger.warn("Local shop inventory {} was stale during purchase sync, missing {} items for {}",
                shopId, remaining, itemId);
        }

        inventories.put(shopId, inventory);
        saveInventory(shopId);
    }

    public void saveInventory(int shopId) {
        ItemStack[] inventory = inventories.get(shopId);
        if (inventory == null) {
            return;
        }

        Path file = inventoriesDir.resolve(shopId + ".json");
        try {
            JsonObject root = new JsonObject();
            root.addProperty("shopId", shopId);

            JsonArray items = new JsonArray();
            root.addProperty("size", inventory.length);
            for (int slot = 0; slot < inventory.length; slot++) {
                ItemStack item = inventory[slot];
                JsonObject itemJson = new JsonObject();
                if (item == null || item.getType() == Material.AIR || item.isEmpty()) {
                    itemJson.addProperty("item_id", "minecraft:air");
                    itemJson.addProperty("quantity", 0);
                    itemJson.addProperty("item_nbt", "");
                } else {
                    itemJson.addProperty("item_id", item.getType().getKey().toString());
                    itemJson.addProperty("quantity", item.getAmount());
                    String nbt = ItemNbtSupport.toNbtString(item);
                    itemJson.addProperty("item_nbt", nbt != null ? nbt : "");
                }
                items.add(itemJson);
            }

            root.add("items", items);
            try (Writer writer = Files.newBufferedWriter(file)) {
                GSON.toJson(root, writer);
            }
        } catch (IOException e) {
            logger.error("Failed to save shop inventory for shop {}", shopId, e);
        }
    }

    public void removeInventory(int shopId) {
        inventories.remove(shopId);
        Path file = inventoriesDir.resolve(shopId + ".json");
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            logger.error("Failed to delete shop inventory file for shop {}", shopId, e);
        }
    }

    public void saveAll() {
        for (Integer shopId : inventories.keySet()) {
            saveInventory(shopId);
        }
    }

    private ItemStack[] loadFromFile(int shopId, Path file) {
        ItemStack[] inventory = new ItemStack[DEFAULT_INVENTORY_SIZE];
        try (Reader reader = Files.newBufferedReader(file)) {
            JsonObject json = GSON.fromJson(reader, JsonObject.class);
            if (json == null || !json.has("items") || !json.get("items").isJsonArray()) {
                logger.warn("Shop inventory file {} is empty or malformed, using blank inventory", file.getFileName());
                return inventory;
            }

            JsonArray items = json.getAsJsonArray("items");
            int storedSize = json.has("size") ? normalizeSize(json.get("size").getAsInt()) : normalizeSize(items.size());
            inventory = new ItemStack[storedSize];
            int limit = Math.min(items.size(), storedSize);
            for (int slot = 0; slot < limit; slot++) {
                JsonObject itemJson = items.get(slot).getAsJsonObject();
                String itemId = itemJson.has("item_id") ? itemJson.get("item_id").getAsString() : "minecraft:air";
                int quantity = itemJson.has("quantity") ? itemJson.get("quantity").getAsInt() : 0;
                String itemNbt = itemJson.has("item_nbt") ? itemJson.get("item_nbt").getAsString() : "";
                if ("minecraft:air".equalsIgnoreCase(itemId) || quantity <= 0) {
                    continue;
                }

                ItemStack stack = ItemNbtSupport.fromNbtString(itemId, itemNbt, quantity);
                if (stack != null && stack.getType() != Material.AIR) {
                    inventory[slot] = stack;
                }
            }
        } catch (IOException e) {
            logger.error("Failed to load shop inventory for shop {}", shopId, e);
        }
        return inventory;
    }

    private ItemStack[] fromShopItems(int shopId, Shop shop, int desiredSize) {
        int normalizedSize = normalizeSize(desiredSize);
        ItemStack[] inventory = new ItemStack[normalizedSize];
        if (shop == null || shop.getItems() == null) {
            return inventory;
        }

        int slot = 0;
        for (ShopItem item : shop.getItems()) {
            if (item == null || item.getTotalCount() <= 0) {
                continue;
            }

            String nbt = (item.getItemNbt() != null && !item.getItemNbt().isBlank()) ? item.getItemNbt() : "";
            ItemStack probe = ItemNbtSupport.fromNbtString(item.getItemId(), nbt, 1);
            if (probe == null || probe.getType() == Material.AIR) {
                logger.warn("Skipping bootstrap item for shop {} because material could not be created: {}", shopId, item.getItemId());
                continue;
            }

            int remaining = item.getTotalCount();
            int maxStack = Math.max(1, probe.getType().getMaxStackSize());
            while (remaining > 0) {
                if (slot >= normalizedSize) {
                    logger.error("Shop {} bootstrap overflow: items do not fit into {} slots", shopId, normalizedSize);
                    return null;
                }

                int amount = Math.min(maxStack, remaining);
                ItemStack stack = ItemNbtSupport.fromNbtString(item.getItemId(), nbt, amount);
                if (stack == null || stack.getType() == Material.AIR) {
                    logger.warn("Failed to create stack during bootstrap for shop {} item {}", shopId, item.getItemId());
                    break;
                }
                inventory[slot++] = stack;
                remaining -= amount;
            }
        }

        return inventory;
    }

    private ItemStack[] normalizeInventory(ItemStack[] inventory, int desiredSize) {
        int normalizedSize = normalizeSize(desiredSize);
        ItemStack[] normalized = new ItemStack[normalizedSize];
        int limit = inventory != null ? Math.min(inventory.length, normalizedSize) : 0;
        for (int slot = 0; slot < limit; slot++) {
            ItemStack item = inventory[slot];
            normalized[slot] = item != null ? item.clone() : null;
        }
        return normalized;
    }

    private ItemStack[] resizeInventory(ItemStack[] inventory, int desiredSize) {
        if (inventory == null) {
            return new ItemStack[desiredSize];
        }
        if (inventory.length == desiredSize) {
            return cloneInventory(inventory);
        }
        return normalizeInventory(inventory, desiredSize);
    }

    private int normalizeSize(int desiredSize) {
        return desiredSize > DEFAULT_INVENTORY_SIZE ? DOUBLE_CHEST_INVENTORY_SIZE : DEFAULT_INVENTORY_SIZE;
    }

    private ItemStack[] cloneInventory(ItemStack[] inventory) {
        int size = inventory != null ? inventory.length : DEFAULT_INVENTORY_SIZE;
        return normalizeInventory(inventory, size);
    }

    private ItemStack[] getMutableInventory(int shopId) {
        ItemStack[] cached = inventories.get(shopId);
        if (cached != null) {
            return cloneInventory(cached);
        }

        Path file = inventoriesDir.resolve(shopId + ".json");
        if (!Files.exists(file)) {
            return null;
        }

        ItemStack[] loaded = loadFromFile(shopId, file);
        inventories.put(shopId, loaded);
        return cloneInventory(loaded);
    }
}
