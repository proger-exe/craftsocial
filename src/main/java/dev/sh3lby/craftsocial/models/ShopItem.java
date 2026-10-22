package dev.sh3lby.craftsocial.models;

import com.google.gson.annotations.SerializedName;

/**
 * Товар в магазине. API возвращает item_id, shop_id, item_nbt (snake_case).
 */
public class ShopItem {
    private int id;
    @SerializedName("item_id")
    private String itemId;
    @SerializedName("item_nbt")
    private String itemNbt;
    @SerializedName("shop_id")
    private int shopId;
    private long price;
    /** Размер лота (доменный параметр позиции). */
    private int quantity;
    /** Физический остаток товара в магазине. */
    @SerializedName("total_count")
    private int totalCount;
    
    public ShopItem() {}
    
    public int getId() {
        return id;
    }
    
    public void setId(int id) {
        this.id = id;
    }
    
    public String getItemId() {
        return itemId;
    }
    
    public void setItemId(String itemId) {
        this.itemId = itemId;
    }

    /** Опциональный NBT (строка). Пустая строка / null = отсутствие NBT. */
    public String getItemNbt() {
        return itemNbt;
    }

    public void setItemNbt(String itemNbt) {
        this.itemNbt = itemNbt;
    }

    public int getShopId() {
        return shopId;
    }
    
    public void setShopId(int shopId) {
        this.shopId = shopId;
    }
    
    public long getPrice() {
        return price;
    }
    
    public void setPrice(long price) {
        this.price = price;
    }
    
    public int getQuantity() {
        return quantity;
    }
    
    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public int getTotalCount() {
        return totalCount;
    }

    public void setTotalCount(int totalCount) {
        this.totalCount = totalCount;
    }
}
