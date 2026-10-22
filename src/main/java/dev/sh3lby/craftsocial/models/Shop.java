package dev.sh3lby.craftsocial.models;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * Магазин.
 */
public class Shop {
    private int id;
    @SerializedName("owner_id")
    private long ownerId;
    private String name;
    private List<ShopItem> items;
    
    public Shop() {}
    
    public int getId() {
        return id;
    }
    
    public void setId(int id) {
        this.id = id;
    }
    
    public long getOwnerId() {
        return ownerId;
    }
    
    public void setOwnerId(long ownerId) {
        this.ownerId = ownerId;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public List<ShopItem> getItems() {
        return items;
    }
    
    public void setItems(List<ShopItem> items) {
        this.items = items;
    }
}
