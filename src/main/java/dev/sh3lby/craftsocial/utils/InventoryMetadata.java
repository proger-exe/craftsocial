package dev.sh3lby.craftsocial.utils;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/**
 * Утилиты для работы с метаданными инвентаря и предметов.
 */
public class InventoryMetadata {
    private static final String NAMESPACE = "craftsocial";
    
    /**
     * Сохраняет shopId в метаданные инвентаря.
     */
    public static void setShopId(Inventory inventory, Plugin plugin, int shopId) {
        // Используем custom data в первом слоте или создаем holder
        // Для простоты используем заголовок инвентаря
    }
    
    /**
     * Получает shopId из метаданных инвентаря.
     */
    public static Integer getShopId(Inventory inventory) {
        if (inventory == null) return null;
        try {
            if (inventory.getViewers().isEmpty()) return null;
            String title = inventory.getViewers().get(0).getOpenInventory().getTitle();
            if (title == null || (!title.startsWith("Магазин: ") && !title.startsWith("Управление магазином: "))) return null;
            String[] parts = title.split(" ");
            for (String part : parts) {
                try {
                    return Integer.parseInt(part);
                } catch (NumberFormatException e) {
                    // Продолжаем поиск
                }
            }
        } catch (Exception e) {
            // Игнорируем
        }
        return null;
    }
    
    /**
     * Сохраняет itemId в метаданные предмета.
     */
    public static void setItemId(ItemStack item, Plugin plugin, int itemId) {
        if (item == null || item.getItemMeta() == null) {
            return;
        }
        
        ItemMeta meta = item.getItemMeta();
        NamespacedKey key = new NamespacedKey(plugin, "shop_item_id");
        PersistentDataContainer container = meta.getPersistentDataContainer();
        container.set(key, PersistentDataType.INTEGER, itemId);
        item.setItemMeta(meta);
    }
    
    /**
     * Получает itemId из метаданных предмета.
     */
    public static Integer getItemId(ItemStack item, Plugin plugin) {
        if (item == null || item.getItemMeta() == null) {
            return null;
        }
        
        ItemMeta meta = item.getItemMeta();
        NamespacedKey key = new NamespacedKey(plugin, "shop_item_id");
        PersistentDataContainer container = meta.getPersistentDataContainer();
        
        if (container.has(key, PersistentDataType.INTEGER)) {
            return container.get(key, PersistentDataType.INTEGER);
        }
        
        return null;
    }
    
    /**
     * Получает clanId из метаданных инвентаря (из заголовка).
     * Поддерживает: "Управление клана: 14 Name", "Клан: Name", "Инвентарь клана: 14".
     */
    public static Integer getClanId(Inventory inventory) {
        if (inventory == null) return null;
        try {
            if (inventory.getViewers().isEmpty()) return null;
            String title = inventory.getViewers().get(0).getOpenInventory().getTitle();
            if (title == null) return null;
            if (title.startsWith("Управление клана: ") || title.startsWith("Инвентарь клана: ") || title.startsWith("Дома клана: ")) {
                String afterPrefix = title.contains(": ") ? title.substring(title.indexOf(": ") + 2) : title;
                String[] parts = afterPrefix.trim().split(" ");
                for (String part : parts) {
                    try {
                        return Integer.parseInt(part);
                    } catch (NumberFormatException e) {
                        // Продолжаем поиск
                    }
                }
            }
            if (title.startsWith("Клан: ")) {
                String[] parts = title.split(" ");
                for (String part : parts) {
                    try {
                        return Integer.parseInt(part);
                    } catch (NumberFormatException e) {
                        // Продолжаем поиск
                    }
                }
            }
        } catch (Exception e) {
            // Игнорируем
        }
        return null;
    }
    
    /**
     * Получает partyId из метаданных инвентаря.
     */
    public static Integer getPartyId(Inventory inventory) {
        if (inventory == null) return null;
        try {
            if (inventory.getViewers().isEmpty()) return null;
            String title = inventory.getViewers().get(0).getOpenInventory().getTitle();
            if (title == null) return null;
            if (title.startsWith("Группа: ") || title.startsWith("Управление группы: ") || title.startsWith("Управление: ") || title.startsWith("Инвентарь группы")) {
                String[] parts = title.split(" ");
                for (String part : parts) {
                    try {
                        return Integer.parseInt(part);
                    } catch (NumberFormatException e) {
                        // Продолжаем поиск
                    }
                }
            }
        } catch (Exception e) {
            // Игнорируем
        }
        return null;
    }
}
