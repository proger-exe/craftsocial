package dev.sh3lby.craftsocial.utils;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Ожидание ввода игрока в чат для редактирования цены/количества товара в manage.
 */
public class PendingShopEdit {
    private static final ConcurrentHashMap<UUID, PendingShopEdit> pending = new ConcurrentHashMap<>();

    private final int shopId;
    private final int itemId;
    private final String field; // "price" | "quantity"

    public PendingShopEdit(int shopId, int itemId, String field) {
        this.shopId = shopId;
        this.itemId = itemId;
        this.field = field;
    }

    public int getShopId() { return shopId; }
    public int getItemId() { return itemId; }
    public String getField() { return field; }

    public static void set(UUID playerId, int shopId, int itemId, String field) {
        pending.put(playerId, new PendingShopEdit(shopId, itemId, field));
    }

    public static PendingShopEdit getAndRemove(UUID playerId) {
        return pending.remove(playerId);
    }
}
