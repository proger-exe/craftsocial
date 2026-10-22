package dev.sh3lby.craftsocial.utils;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Сериализация/десериализация NBT предмета в строку для API магазинов.
 * Формат: JSON от Bukkit ItemStack.serialize() (amount нормализован к 1 для ключа слота).
 */
public final class ItemNbtSupport {
    private static final Logger logger = LoggerFactory.getLogger(ItemNbtSupport.class);
    private static final Gson GSON = new Gson();
    private static final java.lang.reflect.Type MAP_TYPE = new TypeToken<Map<String, Object>>() {}.getType();

    private ItemNbtSupport() {}

    /**
     * Нормализованная строка NBT для слота: один и тот же материал+мета даёт одну и ту же строку.
     * null/air или пустой предмет → "".
     */
    public static String toNbtString(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || item.isEmpty()) {
            return "";
        }
        try {
            ItemStack one = item.clone();
            one.setAmount(1);
            Map<String, Object> map = one.serialize();
            return GSON.toJson(map);
        } catch (Exception e) {
            logger.warn("Failed to serialize ItemStack to NBT string: {}", e.getMessage());
            return "";
        }
    }

    /**
     * Восстанавливает ItemStack из item_id + NBT строки + количество.
     * Если nbt пустой — возвращает простой ItemStack(material, amount).
     */
    public static ItemStack fromNbtString(String itemId, String nbt, int amount) {
        if (amount <= 0) {
            amount = 1;
        }
        if (nbt == null || nbt.isBlank()) {
            Material material = Material.matchMaterial(itemId);
            if (material == null) {
                return null;
            }
            return new ItemStack(material, amount);
        }
        try {
            Map<String, Object> map = GSON.fromJson(nbt.trim(), MAP_TYPE);
            if (map == null) {
                return fallbackStack(itemId, amount);
            }
            // Gson может вернуть Double для чисел — приводим к типам, ожидаемым Bukkit
            map = normalizeDeserializeMap(map);
            ItemStack stack = ItemStack.deserialize(map);
            if (stack == null || stack.getType() == Material.AIR) {
                return fallbackStack(itemId, amount);
            }
            stack.setAmount(Math.min(amount, stack.getType().getMaxStackSize()));
            return stack;
        } catch (Exception e) {
            logger.warn("Failed to deserialize NBT string for item_id={}: {}", itemId, e.getMessage());
            return fallbackStack(itemId, amount);
        }
    }

    private static ItemStack fallbackStack(String itemId, int amount) {
        Material material = Material.matchMaterial(itemId);
        if (material == null) {
            return null;
        }
        return new ItemStack(material, amount);
    }

    /**
     * Нормализация map после Gson: числа как Integer для совместимости с ItemStack.deserialize.
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> normalizeDeserializeMap(Map<String, Object> map) {
        Map<String, Object> out = new HashMap<>();
        for (Map.Entry<String, Object> e : map.entrySet()) {
            String k = e.getKey();
            Object v = e.getValue();
            if ("amount".equals(k) || "v".equals(k)) {
                if (v instanceof Number) {
                    out.put(k, ((Number) v).intValue());
                } else {
                    out.put(k, v);
                }
            } else if (v instanceof Map) {
                out.put(k, normalizeDeserializeMap((Map<String, Object>) v));
            } else {
                out.put(k, v);
            }
        }
        return out;
    }

    /**
     * Ключ слота для группировки и сопоставления: (item_id, item_nbt).
     * Пустой NBT нормализуется к "".
     */
    public static String slotKey(String itemId, String itemNbt) {
        String nbt = (itemNbt != null && !itemNbt.isBlank()) ? itemNbt : "";
        return itemId + "\0" + nbt;
    }
}
