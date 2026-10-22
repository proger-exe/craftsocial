package dev.sh3lby.craftsocial.models;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.google.gson.annotations.SerializedName;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Клан.
 * API возвращает: id, owner_id, members (массив пар [player_id, role]), name, tag, prefix, inventory, balance, homes, created_at, updated_at.
 */
public class Clan {
    private int id;
    private String name;
    private String tag;
    private String description;
    private String prefix;
    @SerializedName("owner_id")
    private int ownerId;
    
    // API: members как массив пар [player_id, role], например [[1, "leader"]]
    @SerializedName("members")
    private List<List<Object>> membersRaw;
    
    // Для удобства: список участников, заполняется лениво из membersRaw в getMembers()
    private transient List<ClanMember> members;
    
    private List<Object> inventory;
    private int balance;
    private List<Map<String, Object>> homes;
    @SerializedName("created_at")
    private String createdAt;
    @SerializedName("updated_at")
    private String updatedAt;
    
    public Clan() {
        this.members = new ArrayList<>();
        this.membersRaw = new ArrayList<>();
        this.inventory = new ArrayList<>();
        this.homes = new ArrayList<>();
    }
    
    public int getId() {
        return id;
    }
    
    public void setId(int id) {
        this.id = id;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public String getTag() {
        return tag;
    }
    
    public void setTag(String tag) {
        this.tag = tag;
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
    
    public String getPrefix() {
        return prefix;
    }
    
    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }
    
    public int getOwnerId() {
        return ownerId;
    }
    
    public void setOwnerId(int ownerId) {
        this.ownerId = ownerId;
    }
    
    /**
     * Список участников. При первом обращении конвертируется из membersRaw ([player_id, role]).
     * Учитывает, что Gson может положить в список JsonPrimitive, а не Number/String.
     */
    public List<ClanMember> getMembers() {
        if (members == null && membersRaw != null) {
            members = new ArrayList<>();
            for (List<Object> pair : membersRaw) {
                if (pair != null && pair.size() >= 2) {
                    try {
                        long playerId = asLong(pair.get(0));
                        String role = asString(pair.get(1));
                        ClanMember member = new ClanMember();
                        member.setPlayerId(playerId);
                        member.setRole(role);
                        member.setClanId(this.id);
                        members.add(member);
                    } catch (Exception ignored) {
                        // пропускаем некорректные записи
                    }
                }
            }
        }
        return members != null ? members : new ArrayList<>();
    }

    private static long asLong(Object o) {
        if (o instanceof Number) return ((Number) o).longValue();
        if (o instanceof JsonPrimitive) return ((JsonPrimitive) o).getAsLong();
        if (o instanceof JsonElement) return ((JsonElement) o).getAsJsonPrimitive().getAsLong();
        throw new IllegalArgumentException("Not a number: " + o);
    }

    private static String asString(Object o) {
        if (o == null) return null;
        if (o instanceof String) return (String) o;
        if (o instanceof JsonPrimitive) return ((JsonPrimitive) o).getAsString();
        if (o instanceof JsonElement) return ((JsonElement) o).getAsString();
        return o.toString();
    }
    
    public void setMembers(List<ClanMember> members) {
        this.members = members;
    }
    
    public List<Object> getInventory() {
        return inventory;
    }
    
    public void setInventory(List<Object> inventory) {
        this.inventory = inventory != null ? inventory : new ArrayList<>();
    }
    
    public int getBalance() {
        return balance;
    }
    
    public void setBalance(int balance) {
        this.balance = balance;
    }
    
    public List<Map<String, Object>> getHomes() {
        return homes;
    }
    
    public void setHomes(List<Map<String, Object>> homes) {
        this.homes = homes != null ? homes : new ArrayList<>();
    }
    
    public String getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }
    
    public String getUpdatedAt() {
        return updatedAt;
    }
    
    public void setUpdatedAt(String updatedAt) {
        this.updatedAt = updatedAt;
    }
    
}
