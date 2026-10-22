package dev.sh3lby.craftsocial.models;

import java.util.ArrayList;
import java.util.List;

/**
 * Группа (Party).
 * Соответствует PartyResponse из MODELS.md
 * members - массив ID участников (List<Long>), а не List<PartyMember>
 */
public class Party {
    private int id;
    private String name;
    private long leaderId;
    private List<Long> members; // массив ID участников согласно MODELS.md
    private String createdAt;
    private String updatedAt;
    
    public Party() {
        this.members = new ArrayList<>();
    }

    public long getLeaderId() {
        return leaderId;
    }

    public void setLeaderId(long leaderId) {
        this.leaderId = leaderId;
    }

    public List<Long> getMembers() {
        return members;
    }

    public void setMembers(List<Long> members) {
        this.members = members != null ? members : new ArrayList<>();
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
