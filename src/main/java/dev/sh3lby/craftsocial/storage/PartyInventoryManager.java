package dev.sh3lby.craftsocial.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Менеджер для локального хранения инвентаря party.
 * Хранит инвентарь в JSON файлах на сервере (не в CRM, так как party временные).
 */
public class PartyInventoryManager {
    private static final Logger logger = LoggerFactory.getLogger(PartyInventoryManager.class);
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    
    private final Plugin plugin;
    private final Path inventoriesDir;
    private final Map<Integer, ItemStack[]> inventories = new ConcurrentHashMap<>();
    
    public PartyInventoryManager(Plugin plugin) {
        this.plugin = plugin;
        this.inventoriesDir = plugin.getDataFolder().toPath().resolve("party-inventories");
        
        // Создаем директорию если не существует
        try {
            if (!Files.exists(inventoriesDir)) {
                Files.createDirectories(inventoriesDir);
            }
        } catch (IOException e) {
            logger.error("Failed to create party inventories directory", e);
        }
    }
    
    /**
     * Загружает инвентарь party из файла.
     */
    public void loadInventory(int partyId) {
        Path file = inventoriesDir.resolve(partyId + ".json");
        
        if (!Files.exists(file)) {
            // Создаем пустой инвентарь
            inventories.put(partyId, new ItemStack[54]);
            return;
        }
        
        try (Reader reader = Files.newBufferedReader(file)) {
            JsonObject json = gson.fromJson(reader, JsonObject.class);
            JsonArray itemsArray = json.getAsJsonArray("items");
            
            ItemStack[] inventory = new ItemStack[54];
            
            for (int i = 0; i < itemsArray.size() && i < 54; i++) {
                JsonObject itemJson = itemsArray.get(i).getAsJsonObject();
                if (itemJson.has("material") && !itemJson.get("material").getAsString().equals("AIR")) {
                    Material material = Material.matchMaterial(itemJson.get("material").getAsString());
                    if (material != null) {
                        int amount = itemJson.has("amount") ? itemJson.get("amount").getAsInt() : 1;
                        ItemStack item = new ItemStack(material, amount);
                        inventory[i] = item;
                    }
                }
            }
            
            inventories.put(partyId, inventory);
            logger.debug("Loaded inventory for party {}", partyId);
        } catch (IOException e) {
            logger.error("Failed to load inventory for party {}", partyId, e);
            inventories.put(partyId, new ItemStack[54]);
        }
    }
    
    /**
     * Сохраняет инвентарь party в файл.
     */
    public void saveInventory(int partyId) {
        ItemStack[] inventory = inventories.get(partyId);
        if (inventory == null) {
            return;
        }
        
        Path file = inventoriesDir.resolve(partyId + ".json");
        
        try {
            JsonObject json = new JsonObject();
            json.addProperty("partyId", partyId);
            
            JsonArray itemsArray = new JsonArray();
            for (ItemStack item : inventory) {
                JsonObject itemJson = new JsonObject();
                if (item != null && item.getType() != Material.AIR) {
                    itemJson.addProperty("material", item.getType().name());
                    itemJson.addProperty("amount", item.getAmount());
                } else {
                    itemJson.addProperty("material", "AIR");
                    itemJson.addProperty("amount", 0);
                }
                itemsArray.add(itemJson);
            }
            
            json.add("items", itemsArray);
            
            try (Writer writer = Files.newBufferedWriter(file)) {
                gson.toJson(json, writer);
            }
            
            logger.debug("Saved inventory for party {}", partyId);
        } catch (IOException e) {
            logger.error("Failed to save inventory for party {}", partyId, e);
        }
    }
    
    /**
     * Получает инвентарь party.
     */
    public ItemStack[] getInventory(int partyId) {
        if (!inventories.containsKey(partyId)) {
            loadInventory(partyId);
        }
        return inventories.get(partyId);
    }
    
    /**
     * Устанавливает инвентарь party.
     */
    public void setInventory(int partyId, ItemStack[] inventory) {
        inventories.put(partyId, inventory);
        saveInventory(partyId);
    }
    
    /**
     * Удаляет инвентарь party.
     */
    public void removeInventory(int partyId) {
        inventories.remove(partyId);
        Path file = inventoriesDir.resolve(partyId + ".json");
        try {
            Files.deleteIfExists(file);
            logger.debug("Removed inventory for party {}", partyId);
        } catch (IOException e) {
            logger.error("Failed to remove inventory file for party {}", partyId, e);
        }
    }
    
    /**
     * Сохраняет все инвентари при выключении сервера.
     */
    public void saveAll() {
        for (Integer partyId : inventories.keySet()) {
            saveInventory(partyId);
        }
        logger.info("Saved all party inventories");
    }
    
    /**
     * Загружает все инвентари активных party при старте сервера.
     * Вызывается с списком активных party ID.
     */
    public void loadAll(java.util.List<Integer> activePartyIds) {
        for (Integer partyId : activePartyIds) {
            loadInventory(partyId);
        }
        logger.info("Loaded {} party inventories", activePartyIds.size());
    }
}
