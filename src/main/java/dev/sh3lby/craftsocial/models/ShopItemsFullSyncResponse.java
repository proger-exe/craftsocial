package dev.sh3lby.craftsocial.models;

import java.util.List;

/**
 * Ответ API на full-sync товаров магазина.
 * PUT /user/shops/{shop_id}/items/full-sync
 */
public class ShopItemsFullSyncResponse {
    private int created;
    private int updated;
    private int deleted;
    private int total;
    private List<ShopItem> items;

    public int getCreated() {
        return created;
    }

    public void setCreated(int created) {
        this.created = created;
    }

    public int getUpdated() {
        return updated;
    }

    public void setUpdated(int updated) {
        this.updated = updated;
    }

    public int getDeleted() {
        return deleted;
    }

    public void setDeleted(int deleted) {
        this.deleted = deleted;
    }

    public int getTotal() {
        return total;
    }

    public void setTotal(int total) {
        this.total = total;
    }

    public List<ShopItem> getItems() {
        return items;
    }

    public void setItems(List<ShopItem> items) {
        this.items = items;
    }
}
