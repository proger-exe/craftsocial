package dev.sh3lby.craftsocial.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.plugin.Plugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Менеджер для хранения привязки магазинов к локациям сундуков.
 */
public class ShopLocationManager {
    private static final Logger logger = LoggerFactory.getLogger(ShopLocationManager.class);
    
    private final Plugin plugin;
    private final File dataFile;
    private final Gson gson;
    private final ConcurrentHashMap<Integer, Location> shopLocations = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, Long> shopOwners = new ConcurrentHashMap<>();
    
    public ShopLocationManager(Plugin plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "shop_locations.json");
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        loadLocations();
    }
    
    /**
     * Сохраняет привязку магазина к локации.
     */
    public void setShopLocation(int shopId, Location location) {
        shopLocations.put(shopId, location);
        saveLocations();
        logger.debug("Shop {} bound to location {}", shopId, location);
    }

    public void setShopLocation(int shopId, Location location, long ownerId) {
        shopLocations.put(shopId, location);
        shopOwners.put(shopId, ownerId);
        saveLocations();
        logger.debug("Shop {} bound to location {} for owner {}", shopId, location, ownerId);
    }
    
    /**
     * Получает локацию магазина.
     */
    public Location getShopLocation(int shopId) {
        return shopLocations.get(shopId);
    }
    
    /**
     * Удаляет привязку магазина.
     */
    public void removeShopLocation(int shopId) {
        shopLocations.remove(shopId);
        shopOwners.remove(shopId);
        saveLocations();
        logger.debug("Shop {} location removed", shopId);
    }

    public void setShopOwner(int shopId, long ownerId) {
        shopOwners.put(shopId, ownerId);
        saveLocations();
    }

    public Long getShopOwnerId(int shopId) {
        return shopOwners.get(shopId);
    }
    
    /**
     * Получает ID магазина по локации.
     */
    public Integer getShopIdByLocation(Location location) {
        for (Map.Entry<Integer, Location> entry : shopLocations.entrySet()) {
            Location loc = entry.getValue();
            if (loc != null && loc.getWorld() != null && location.getWorld() != null &&
                loc.getWorld().getName().equals(location.getWorld().getName()) &&
                loc.getBlockX() == location.getBlockX() &&
                loc.getBlockY() == location.getBlockY() &&
                loc.getBlockZ() == location.getBlockZ()) {
                return entry.getKey();
            }
        }
        return null;
    }
    
    /**
     * Загружает привязки из файла.
     */
    private void loadLocations() {
        if (!dataFile.exists()) {
            return;
        }
        
        try (FileReader reader = new FileReader(dataFile)) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
            
            for (Map.Entry<String, com.google.gson.JsonElement> entry : json.entrySet()) {
                int shopId = Integer.parseInt(entry.getKey());
                JsonObject locJson = entry.getValue().getAsJsonObject();
                
                String worldName = locJson.get("world").getAsString();
                int x = locJson.get("x").getAsInt();
                int y = locJson.get("y").getAsInt();
                int z = locJson.get("z").getAsInt();
                if (locJson.has("owner_id") && !locJson.get("owner_id").isJsonNull()) {
                    shopOwners.put(shopId, locJson.get("owner_id").getAsLong());
                }
                
                org.bukkit.World world = Bukkit.getWorld(worldName);
                if (world != null) {
                    Location location = new Location(world, x, y, z);
                    shopLocations.put(shopId, location);
                }
            }
            
            logger.info("Loaded {} shop locations", shopLocations.size());
        } catch (IOException e) {
            logger.error("Failed to load shop locations", e);
        }
    }
    
    /**
     * Сохраняет привязки в файл.
     */
    private void saveLocations() {
        try {
            if (!plugin.getDataFolder().exists()) {
                plugin.getDataFolder().mkdirs();
            }
            
            JsonObject json = new JsonObject();
            for (Map.Entry<Integer, Location> entry : shopLocations.entrySet()) {
                Location loc = entry.getValue();
                if (loc != null && loc.getWorld() != null) {
                    JsonObject locJson = new JsonObject();
                    locJson.addProperty("world", loc.getWorld().getName());
                    locJson.addProperty("x", loc.getBlockX());
                    locJson.addProperty("y", loc.getBlockY());
                    locJson.addProperty("z", loc.getBlockZ());
                    Long ownerId = shopOwners.get(entry.getKey());
                    if (ownerId != null) {
                        locJson.addProperty("owner_id", ownerId);
                    }
                    json.add(String.valueOf(entry.getKey()), locJson);
                }
            }
            
            try (FileWriter writer = new FileWriter(dataFile)) {
                gson.toJson(json, writer);
            }
        } catch (IOException e) {
            logger.error("Failed to save shop locations", e);
        }
    }
    
    /**
     * Сохраняет все привязки.
     */
    public void saveAll() {
        saveLocations();
    }
}
