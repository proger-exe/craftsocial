package dev.sh3lby.craftsocial.models;

import com.google.gson.annotations.SerializedName;

/**
 * Ответ API на перевод между игроками (POST /user/transactions/transfer).
 */
public class TransferResponse {
    @SerializedName("transaction_id")
    private int transactionId;
    @SerializedName("from_player_id")
    private long fromPlayerId;
    @SerializedName("to_player_id")
    private long toPlayerId;
    private int amount;
    @SerializedName("from_balance_after")
    private int fromBalanceAfter;
    @SerializedName("to_balance_after")
    private int toBalanceAfter;
    @SerializedName("created_at")
    private String createdAt;

    public int getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(int transactionId) {
        this.transactionId = transactionId;
    }

    public long getFromPlayerId() {
        return fromPlayerId;
    }

    public void setFromPlayerId(long fromPlayerId) {
        this.fromPlayerId = fromPlayerId;
    }

    public long getToPlayerId() {
        return toPlayerId;
    }

    public void setToPlayerId(long toPlayerId) {
        this.toPlayerId = toPlayerId;
    }

    public int getAmount() {
        return amount;
    }

    public void setAmount(int amount) {
        this.amount = amount;
    }

    public int getFromBalanceAfter() {
        return fromBalanceAfter;
    }

    public void setFromBalanceAfter(int fromBalanceAfter) {
        this.fromBalanceAfter = fromBalanceAfter;
    }

    public int getToBalanceAfter() {
        return toBalanceAfter;
    }

    public void setToBalanceAfter(int toBalanceAfter) {
        this.toBalanceAfter = toBalanceAfter;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }
}
