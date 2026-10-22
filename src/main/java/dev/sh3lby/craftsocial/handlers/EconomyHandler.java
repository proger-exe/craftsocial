package dev.sh3lby.craftsocial.handlers;

import dev.sh3lby.craftsocial.api.ApiClient;
import dev.sh3lby.craftsocial.models.BalanceResponse;
import dev.sh3lby.craftsocial.models.Transaction;
import dev.sh3lby.craftsocial.models.TransferResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;

/**
 * Обработчик экономики.
 * Работает с транзакциями через /user/transactions.
 */
public class EconomyHandler {
    private static final Logger logger = LoggerFactory.getLogger(EconomyHandler.class);
    
    private final ApiClient apiClient;
    
    public EconomyHandler(ApiClient apiClient) {
        this.apiClient = apiClient;
    }
    
    /**
     * Получает баланс текущего игрока (GET /user/balance, заголовок X-Player-ID).
     * Ответ API: {"balance": 123}.
     */
    public CompletableFuture<Integer> getBalance(long playerId) {
        return apiClient.get("/user/balance", BalanceResponse.class, playerId)
            .thenApply(response -> {
                if (response.isSuccess() && response.getData() != null) {
                    return response.getData().getBalance();
                }
                logger.warn("Failed to get balance for player {}: {}", playerId, response.getError());
                return null;
            });
    }
    
    /**
     * Топ игроков по балансу (GET /user/rankings/balance).
     * Ответ API: массив {player_id, nickname, balance, rank}.
     */
    public CompletableFuture<java.util.List<java.util.Map<String, Object>>> getTopByBalance(int limit, long playerId) {
        String endpoint = "/user/rankings/balance?limit=" + limit;
        return apiClient.get(endpoint, java.util.List.class, playerId)
            .thenApply(response -> {
                if (response.isSuccess() && response.getRawJson() != null) {
                    return dev.sh3lby.craftsocial.utils.GsonUtils.deserializeList(
                        response.getRawJson(),
                        (Class<java.util.Map<String, Object>>) (Class<?>) java.util.Map.class
                    );
                }
                logger.warn("Failed to get top balance: {}", response.getError());
                return null;
            });
    }

    /**
     * Создает транзакцию (перевод, платеж, награда и т.д.).
     */
    public CompletableFuture<Transaction> createTransaction(Transaction transaction, long playerId) {
        return apiClient.post("/user/transactions", transaction, Transaction.class, playerId)
            .thenApply(response -> {
                if (response.isSuccess() && response.getData() != null) {
                    logger.debug("Transaction created successfully: {}", response.getData().getId());
                    return response.getData();
                } else {
                    logger.warn("Failed to create transaction: {}", response.getError());
                    throw new RuntimeException("Failed to create transaction: " + response.getError());
                }
            });
    }
    
    /**
     * Переводит средства между игроками через отдельный endpoint:
     * POST /user/transactions/transfer (from берется из X-Player-ID).
     */
    public CompletableFuture<TransferResponse> transfer(long fromPlayerId, long toPlayerId, int amount, String description) {
        java.util.Map<String, Object> request = new java.util.HashMap<>();
        request.put("to_player_id", toPlayerId);
        request.put("amount", amount);
        if (description != null && !description.trim().isEmpty()) {
            request.put("description", description.trim());
        }

        return apiClient.post("/user/transactions/transfer", request, TransferResponse.class, fromPlayerId)
            .thenApply(response -> {
                if (response.isSuccess() && response.getData() != null) {
                    return response.getData();
                }
                String message = formatTransferError(response.getStatusCode(), response.getError(), response.getDetail());
                throw new RuntimeException(message);
            });
    }

    private String formatTransferError(int statusCode, String error, String detail) {
        switch (statusCode) {
            case 400:
                return detail != null && !detail.isEmpty() ? detail : "Некорректные параметры перевода";
            case 401:
                return "Ошибка авторизации перевода";
            case 402:
                return "Недостаточно средств";
            case 404:
                return detail != null && !detail.isEmpty() ? detail : "Получатель или его профиль не найден";
            case 500:
                return "Внутренняя ошибка сервиса переводов";
            default:
                if (detail != null && !detail.isEmpty()) return detail;
                if (error != null && !error.isEmpty()) return error;
                return "Ошибка перевода";
        }
    }
    
    /**
     * Создает платеж (для покупок в магазинах).
     */
    public CompletableFuture<Transaction> createPayment(long fromPlayerId, long toPlayerId, int amount, String description, Integer relatedEntityId) {
        Transaction transaction = new Transaction();
        transaction.setAmount(amount);
        transaction.setTransactionType("payment");
        transaction.setDescription(description != null ? description : "Payment");
        transaction.setFromPlayerId(fromPlayerId);
        transaction.setToPlayerId(toPlayerId);
        transaction.setRelatedEntityType("shop");
        transaction.setRelatedEntityId(relatedEntityId);
        
        return createTransaction(transaction, fromPlayerId);
    }
    
    /**
     * Выдает награду игроку.
     */
    public CompletableFuture<Transaction> giveReward(long toPlayerId, int amount, String description, String relatedEntityType, Integer relatedEntityId) {
        Transaction transaction = new Transaction();
        transaction.setAmount(amount);
        transaction.setTransactionType("reward");
        transaction.setDescription(description != null ? description : "Reward");
        transaction.setToPlayerId(toPlayerId);
        transaction.setRelatedEntityType(relatedEntityType);
        transaction.setRelatedEntityId(relatedEntityId);
        
        return createTransaction(transaction, toPlayerId);
    }
    
    /**
     * Получает транзакцию по ID.
     */
    public CompletableFuture<Transaction> getTransaction(int transactionId, long playerId) {
        return apiClient.get("/user/transactions/" + transactionId, Transaction.class, playerId)
            .thenApply(response -> {
                if (response.isSuccess() && response.getData() != null) {
                    return response.getData();
                } else {
                    logger.warn("Failed to get transaction {}: {}", transactionId, response.getError());
                    return null;
                }
            });
    }
    
    /**
     * Получает список транзакций с фильтрами.
     */
    public CompletableFuture<java.util.List<Transaction>> getTransactions(
            long playerId, 
            String transactionType, 
            String startDate, 
            String endDate, 
            Integer page, 
            Integer perPage) {
        StringBuilder endpoint = new StringBuilder("/user/transactions?");
        boolean hasParams = false;
        if (transactionType != null) {
            endpoint.append("transaction_type=").append(transactionType);
            hasParams = true;
        }
        if (startDate != null) {
            if (hasParams) endpoint.append("&");
            endpoint.append("start_date=").append(startDate);
            hasParams = true;
        }
        if (endDate != null) {
            if (hasParams) endpoint.append("&");
            endpoint.append("end_date=").append(endDate);
            hasParams = true;
        }
        if (page != null) {
            if (hasParams) endpoint.append("&");
            else endpoint.append("?");
            endpoint.append("page=").append(page);
            hasParams = true;
        }
        if (perPage != null) {
            if (hasParams) endpoint.append("&");
            else endpoint.append("?");
            endpoint.append("per_page=").append(perPage);
        }
        
        return apiClient.get(endpoint.toString(), java.util.List.class, playerId)
            .thenApply(response -> {
                if (response.isSuccess() && response.getData() != null) {
                    // Десериализация списка транзакций
                    if (response.getRawJson() != null) {
                        return dev.sh3lby.craftsocial.utils.GsonUtils.deserializeList(
                            response.getRawJson(), 
                            Transaction.class
                        );
                    }
                    return new java.util.ArrayList<>();
                } else {
                    logger.warn("Failed to get transactions for player {}: {}", playerId, response.getError());
                    return null;
                }
            });
    }
}
