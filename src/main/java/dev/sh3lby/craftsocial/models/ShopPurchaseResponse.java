package dev.sh3lby.craftsocial.models;

import com.google.gson.annotations.SerializedName;

/**
 * Ответ API на покупку в магазине (POST /user/shops/{shop_id}/items/{item_id}/buy).
 * См. SHOP_PURCHASE.md.
 */
public class ShopPurchaseResponse {
    @SerializedName("shop_item_id")
    private int shopItemId;
    @SerializedName("item_id")
    private String itemId;
    @SerializedName("item_nbt")
    private String itemNbt;
    private int quantity;
    @SerializedName("total_paid")
    private int totalPaid;
    @SerializedName("remaining_total_count")
    private int remainingTotalCount;

    public int getShopItemId() {
        return shopItemId;
    }

    public void setShopItemId(int shopItemId) {
        this.shopItemId = shopItemId;
    }

    public String getItemId() {
        return itemId;
    }

    public void setItemId(String itemId) {
        this.itemId = itemId;
    }

    /** NBT выданного предмета (если бэкенд отдаёт). */
    public String getItemNbt() {
        return itemNbt;
    }

    public void setItemNbt(String itemNbt) {
        this.itemNbt = itemNbt;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public int getTotalPaid() {
        return totalPaid;
    }

    public void setTotalPaid(int totalPaid) {
        this.totalPaid = totalPaid;
    }

    public int getRemainingTotalCount() {
        return remainingTotalCount;
    }

    public void setRemainingTotalCount(int remainingTotalCount) {
        this.remainingTotalCount = remainingTotalCount;
    }
}
