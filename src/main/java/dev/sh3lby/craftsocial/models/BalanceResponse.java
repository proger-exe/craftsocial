package dev.sh3lby.craftsocial.models;

/**
 * Ответ API GET /user/balance.
 * {"balance": 123}
 */
public class BalanceResponse {
    private int balance;

    public int getBalance() {
        return balance;
    }

    public void setBalance(int balance) {
        this.balance = balance;
    }
}
