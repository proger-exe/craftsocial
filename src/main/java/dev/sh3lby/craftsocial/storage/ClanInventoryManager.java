package dev.sh3lby.craftsocial.storage;

import dev.sh3lby.craftsocial.handlers.ClanHandler;
import dev.sh3lby.craftsocial.utils.ItemNbtSupport;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Менеджер инвентаря клана: локальный кеш + сохранение в API при изменениях и подтяжка с API при открытии и по таймеру.
 * Индекс 0..53 — сетка, 54 — курсор.
 * При ошибке записи в API выполняется одна повторная попытка через 20 тиков.
 */
public class ClanInventoryManager {
    private static final Logger logger = LoggerFactory.getLogger(ClanInventoryManager.class);
    private static final int INV_SIZE = 54;
    /** Индекс слота курсора в массиве из 55 элементов (0..53 — сетка, 54 — курсор). */
    public static final int CURSOR_SLOT = 54;
    private static final int TOTAL_SLOTS = 55;

    /** Время последнего forced update (setInventory) по clanId; используется чтобы не перезаписывать кеш из API в течение 8 сек. */
    private final ConcurrentHashMap<Integer, Long> lastForcedUpdateTimeMs = new ConcurrentHashMap<>();

    /** Одна повторная попытка сохранения при ошибке API: clanId -> (apiList, playerId). */
    private final ConcurrentHashMap<Integer, PendingApiSave> pendingRetry = new ConcurrentHashMap<>();
    /** Для каждого clanId не более одной запланированной задачи retry. */
    private final ConcurrentHashMap<Integer, Boolean> retryScheduled = new ConcurrentHashMap<>();

    private final ClanHandler clanHandler;
    private final Plugin plugin;
    private final ConcurrentHashMap<Integer, ItemStack[]> cache = new ConcurrentHashMap<>();

    public ClanInventoryManager(ClanHandler clanHandler, Plugin plugin) {
        this.clanHandler = clanHandler;
        this.plugin = plugin;
    }

    private static final class PendingApiSave {
        final List<Object> apiList;
        final long playerId;

        PendingApiSave(List<Object> apiList, long playerId) {
            this.apiList = new ArrayList<>(apiList);
            this.playerId = playerId;
        }
    }

    /**
     * Возвращает true, если для данного клана недавно был forced update (setInventory);
     * в течение 8 секунд после него 8-тиковый синк с API для этого клана нужно пропускать.
     */
    public boolean shouldSkipApiSync(int clanId) {
        Long t = lastForcedUpdateTimeMs.get(clanId);
        if (t == null) return false;
        return (System.currentTimeMillis() - t) < 8_000L;
    }

    /**
     * Возвращает клон инвентаря клана из кеша или новый пустой массив ItemStack[55], если записи нет.
     */
    public ItemStack[] getInventory(int clanId) {
        ItemStack[] cached = cache.get(clanId);
        if (cached == null) {
            return new ItemStack[TOTAL_SLOTS];
        }
        ItemStack[] clone = new ItemStack[TOTAL_SLOTS];
        for (int i = 0; i < TOTAL_SLOTS; i++) {
            clone[i] = cached[i] != null ? cached[i].clone() : null;
        }
        return clone;
    }

    /**
     * Обновляет кеш данными, полученными с API (без вызова API). Используется при открытии и в refresh-таске.
     */
    public void putCacheFromApi(int clanId, ItemStack[] full) {
        if (full == null || full.length < TOTAL_SLOTS) return;
        ItemStack[] copy = new ItemStack[TOTAL_SLOTS];
        for (int i = 0; i < TOTAL_SLOTS; i++) {
            copy[i] = full[i] != null ? full[i].clone() : null;
        }
        cache.put(clanId, copy);
    }

    /**
     * Разбирает ответ API (список слотов с item_id, quantity, item_nbt) в ItemStack[55]. Публичный для refresh-таска и GUI.
     */
    public static ItemStack[] fromApiInventory(List<Object> list) {
        ItemStack[] inv = new ItemStack[TOTAL_SLOTS];
        if (list == null) return inv;
        int size = Math.min(list.size(), TOTAL_SLOTS);
        for (int slot = 0; slot < size; slot++) {
            Object o = list.get(slot);
            if (!(o instanceof Map)) continue;
            @SuppressWarnings("unchecked")
            Map<String, Object> m = (Map<String, Object>) o;
            String itemId = m.get("item_id") != null ? m.get("item_id").toString().trim() : "";
            if (itemId.isEmpty() || "minecraft:air".equalsIgnoreCase(itemId)) continue;
            int quantity = m.get("quantity") instanceof Number ? ((Number) m.get("quantity")).intValue() : 1;
            if (quantity <= 0) continue;
            Object nbtObj = m.get("item_nbt");
            String itemNbt = (nbtObj != null && !nbtObj.toString().trim().isEmpty()) ? nbtObj.toString().trim() : "";
            ItemStack stack = ItemNbtSupport.fromNbtString(itemId, itemNbt, quantity);
            if (stack != null && !stack.getType().isAir())
                inv[slot] = stack;
        }
        return inv;
    }

    private static List<Object> toApiInventory(ItemStack[] inv) {
        int len = inv != null ? Math.min(inv.length, TOTAL_SLOTS) : 0;
        List<Object> list = new ArrayList<>(TOTAL_SLOTS);
        for (int slot = 0; slot < TOTAL_SLOTS; slot++) {
            ItemStack item = (slot < len && inv[slot] != null) ? inv[slot] : null;
            Map<String, Object> m = new java.util.HashMap<>();
            if (item == null || item.getType() == Material.AIR || item.isEmpty()) {
                m.put("item_id", "minecraft:air");
                m.put("quantity", 0);
                m.put("item_nbt", "");
            } else {
                m.put("item_id", item.getType().getKey().toString());
                m.put("quantity", item.getAmount());
                String nbt = ItemNbtSupport.toNbtString(item);
                m.put("item_nbt", nbt != null ? nbt : "");
            }
            list.add(m);
        }
        return list;
    }

    /**
     * Обновляет кеш и сохраняет в API (долгосрочное хранение). Callback для рассылки зрителям вызывается сразу после обновления кеша.
     */
    public void setInventory(int clanId, ItemStack[] grid, ItemStack cursor, long playerId) {
        setInventory(clanId, grid, cursor, playerId, null);
    }

    /**
     * То же, но сразу после обновления кеша вызывается onSavedWithGrid с клоном сетки для отложенной рассылки зрителям.
     * Запись в API выполняется в фоне (fire-and-forget).
     */
    public void setInventory(int clanId, ItemStack[] grid, ItemStack cursor, long playerId, Consumer<ItemStack[]> onSavedWithGrid) {
        if (grid == null) grid = new ItemStack[INV_SIZE];
        ItemStack[] full = new ItemStack[TOTAL_SLOTS];
        for (int i = 0; i < INV_SIZE && i < grid.length; i++) full[i] = grid[i];
        full[CURSOR_SLOT] = (cursor != null && !cursor.getType().isAir() && !cursor.isEmpty()) ? cursor : null;
        cache.put(clanId, full);
        lastForcedUpdateTimeMs.put(clanId, System.currentTimeMillis());
        List<Object> apiList = toApiInventory(full);
        clanHandler.updateClanInventory(clanId, apiList, playerId)
                .thenAccept(clan -> {})
                .exceptionally(ex -> {
                    logger.warn("Failed to save clan inventory for clan {}, will retry in 20 ticks: {}", clanId, ex.getMessage());
                    pendingRetry.put(clanId, new PendingApiSave(apiList, playerId));
                    if (retryScheduled.putIfAbsent(clanId, Boolean.TRUE) == null) {
                        org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () -> {
                            retryScheduled.remove(clanId);
                            retrySave(clanId);
                        }, 20L);
                    }
                    return null;
                });
        if (onSavedWithGrid != null) {
            ItemStack[] gridClone = new ItemStack[INV_SIZE];
            for (int i = 0; i < INV_SIZE; i++) gridClone[i] = full[i] != null ? full[i].clone() : null;
            onSavedWithGrid.accept(gridClone);
        }
    }

    private void retrySave(int clanId) {
        PendingApiSave pending = pendingRetry.remove(clanId);
        if (pending == null) return;
        clanHandler.updateClanInventory(clanId, pending.apiList, pending.playerId)
                .thenAccept(clan -> {
                    if (clan != null) logger.debug("Clan inventory save retry succeeded for clan {}", clanId);
                })
                .exceptionally(ex -> {
                    logger.warn("Clan inventory save retry failed for clan {}: {}", clanId, ex.getMessage());
                    return null;
                });
    }
}
