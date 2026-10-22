package dev.sh3lby.craftsocial.models;

import com.google.gson.annotations.SerializedName;

/**
 * Транзакция.
 * Соответствует TransactionCreate/TransactionResponse из MODELS.md (API ожидает snake_case).
 */
public class Transaction {
    private int id;
    private int amount;
    @SerializedName("transaction_type")
    private String transactionType; // deposit, withdrawal, transfer, reward, payment, refund
    private String description;
    @SerializedName("from_player_id")
    private Long fromPlayerId;
    @SerializedName("to_player_id")
    private Long toPlayerId;
    @SerializedName("related_entity_type")
    private String relatedEntityType;
    @SerializedName("related_entity_id")
    private Integer relatedEntityId;
    @SerializedName("created_at")
    private String createdAt;
    
    public Transaction() {}
    
    public int getId() {
        return id;
    }
    
    public void setId(int id) {
        this.id = id;
    }
    
    public int getAmount() {
        return amount;
    }
    
    public void setAmount(int amount) {
        this.amount = amount;
    }
    
    public String getTransactionType() {
        return transactionType;
    }
    
    public void setTransactionType(String transactionType) {
        this.transactionType = transactionType;
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
    
    public Long getFromPlayerId() {
        return fromPlayerId;
    }
    
    public void setFromPlayerId(Long fromPlayerId) {
        this.fromPlayerId = fromPlayerId;
    }
    
    public Long getToPlayerId() {
        return toPlayerId;
    }
    
    public void setToPlayerId(Long toPlayerId) {
        this.toPlayerId = toPlayerId;
    }
    
    public String getRelatedEntityType() {
        return relatedEntityType;
    }
    
    public void setRelatedEntityType(String relatedEntityType) {
        this.relatedEntityType = relatedEntityType;
    }
    
    public Integer getRelatedEntityId() {
        return relatedEntityId;
    }
    
    public void setRelatedEntityId(Integer relatedEntityId) {
        this.relatedEntityId = relatedEntityId;
    }
    
    public String getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }
}
