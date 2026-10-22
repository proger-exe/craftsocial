package dev.sh3lby.craftsocial.api;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.sh3lby.craftsocial.models.Clan;
import dev.sh3lby.craftsocial.models.ClanDeserializer;
import dev.sh3lby.craftsocial.async.AsyncTaskManager;
import dev.sh3lby.craftsocial.config.PluginConfig;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Полностью асинхронный HTTP клиент для работы с CraftCore API.
 * Все методы возвращают CompletableFuture для неблокирующих операций.
 */
public class ApiClient {
    private static final Logger logger = LoggerFactory.getLogger(ApiClient.class);
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    /** Bootstrap system/owner account id — such calls must carry a service JWT. */
    public static final long SYSTEM_PLAYER_ID = 777000L;

    private final PluginConfig config;
    private final AsyncTaskManager asyncTaskManager;
    private final OkHttpClient httpClient;
    private final Gson gson;
    private final String baseUrl;
    private final ServiceTokenProvider serviceTokenProvider;

    public ApiClient(PluginConfig config, AsyncTaskManager asyncTaskManager) {
        this.config = config;
        this.asyncTaskManager = asyncTaskManager;
        this.baseUrl = config.getApiBaseUrl();
        this.gson = new GsonBuilder()
            .registerTypeAdapter(Clan.class, new ClanDeserializer())
            .create();

        // Настройка OkHttp клиента с таймаутами
        this.httpClient = new OkHttpClient.Builder()
            .connectTimeout(config.getApiTimeout(), TimeUnit.SECONDS)
            .readTimeout(config.getApiTimeout(), TimeUnit.SECONDS)
            .writeTimeout(config.getApiTimeout(), TimeUnit.SECONDS)
            .build();
        this.serviceTokenProvider = new ServiceTokenProvider(config, httpClient);
    }

    /** Attach Authorization: Bearer for system-account calls (X-Player-ID: 777000). */
    private void applyPlayerAuth(Request.Builder builder, long playerId) {
        builder.header("X-Player-ID", String.valueOf(playerId));
        if (playerId == SYSTEM_PLAYER_ID) {
            String token = serviceTokenProvider.getToken();
            if (token != null) {
                builder.header("Authorization", "Bearer " + token);
            } else {
                logger.warn("System-account call without service token — configure api.service-auth.client-secret");
            }
        }
    }
    
    /**
     * Выполняет GET запрос асинхронно.
     */
    public <T> CompletableFuture<ApiResponse<T>> get(String endpoint, Class<T> responseType, long playerId) {
        return asyncTaskManager.execute(() -> {
            String url = baseUrl + endpoint;
            Request.Builder builder = new Request.Builder().url(url).get();
            applyPlayerAuth(builder, playerId);
            return executeRequest(builder.build(), responseType);
        });
    }
    
    /**
     * Выполняет POST запрос асинхронно.
     */
    public <T> CompletableFuture<ApiResponse<T>> post(String endpoint, Object body, Class<T> responseType, long playerId) {
        return asyncTaskManager.execute(() -> {
            String url = baseUrl + endpoint;
            Request.Builder requestBuilder = new Request.Builder()
                .url(url)
                .header("Content-Type", "application/json");
            applyPlayerAuth(requestBuilder, playerId);

            if (body != null) {
                String jsonBody = gson.toJson(body);
                RequestBody requestBody = RequestBody.create(jsonBody, JSON);
                requestBuilder.post(requestBody);
            } else {
                requestBuilder.post(RequestBody.create("{}", JSON));
            }

            Request request = requestBuilder.build();
            return executeRequest(request, responseType);
        });
    }
    
    /**
     * Выполняет POST запрос асинхронно без player_id в заголовке.
     * Используется для создания новых профилей, когда player_id еще неизвестен.
     */
    public <T> CompletableFuture<ApiResponse<T>> postWithoutPlayerId(String endpoint, Object body, Class<T> responseType) {
        return asyncTaskManager.execute(() -> {
            String url = baseUrl + endpoint;
            Request.Builder requestBuilder = new Request.Builder()
                .url(url)
                .header("Content-Type", "application/json");
            
            if (body != null) {
                String jsonBody = gson.toJson(body);
                RequestBody requestBody = RequestBody.create(jsonBody, JSON);
                requestBuilder.post(requestBody);
            } else {
                requestBuilder.post(RequestBody.create("{}", JSON));
            }
            
            Request request = requestBuilder.build();
            return executeRequest(request, responseType);
        });
    }
    
    /**
     * Выполняет PUT запрос асинхронно.
     */
    public <T> CompletableFuture<ApiResponse<T>> put(String endpoint, Object body, Class<T> responseType, long playerId) {
        return asyncTaskManager.execute(() -> {
            String url = baseUrl + endpoint;
            String jsonBody = gson.toJson(body);
            RequestBody requestBody = RequestBody.create(jsonBody, JSON);

            Request.Builder builder = new Request.Builder()
                .url(url)
                .header("Content-Type", "application/json")
                .put(requestBody);
            applyPlayerAuth(builder, playerId);
            return executeRequest(builder.build(), responseType);
        });
    }
    
    /**
     * Выполняет DELETE запрос асинхронно.
     */
    public <T> CompletableFuture<ApiResponse<T>> delete(String endpoint, Class<T> responseType, long playerId) {
        return asyncTaskManager.execute(() -> {
            String url = baseUrl + endpoint;
            Request.Builder builder = new Request.Builder().url(url).delete();
            applyPlayerAuth(builder, playerId);
            return executeRequest(builder.build(), responseType);
        });
    }
    
    /**
     * Выполняет запрос с retry логикой.
     */
    private <T> ApiResponse<T> executeRequest(Request request, Class<T> responseType) throws ApiException {
        int attempts = 0;
        long delay = config.getRetryDelay();
        
        while (attempts < config.getRetryMaxAttempts()) {
            attempts++;
            
            String method = request.method();
            String url = request.url().toString();
            logger.info("API -> {} {}", method, url);
            
            try (Response response = httpClient.newCall(request).execute()) {
                String responseBody = response.body() != null ? response.body().string() : "{}";
                int statusCode = response.code();
                
                // Лог ответа: код + тело (обрезаем если длинное)
                int maxLogLen = config.isDebug() ? 4096 : 1024;
                String bodyPreview = responseBody == null ? "null" : responseBody.trim();
                if (bodyPreview.length() > maxLogLen) {
                    bodyPreview = bodyPreview.substring(0, maxLogLen) + "... [" + bodyPreview.length() + " chars]";
                }
                logger.info("API {} {} -> {} | {}", method, url, statusCode, bodyPreview);
                
                // Обработка пустого ответа
                if (responseBody == null || responseBody.trim().isEmpty()) {
                    responseBody = "{}";
                }
                
                JsonObject jsonObject;
                try {
                    // Пытаемся распарсить как объект
                    jsonObject = JsonParser.parseString(responseBody).getAsJsonObject();
                } catch (Exception e) {
                    // Если ответ не объект, возможно это массив - создаем объект с массивом
                    try {
                        com.google.gson.JsonElement element = JsonParser.parseString(responseBody);
                        if (element.isJsonArray()) {
                            jsonObject = new JsonObject();
                            jsonObject.add("items", element.getAsJsonArray());
                        } else {
                            jsonObject = new JsonObject();
                        }
                    } catch (Exception e2) {
                        logger.warn("Failed to parse response as JSON: {}", responseBody);
                        jsonObject = new JsonObject();
                    }
                }
                
                if (response.isSuccessful()) {
                    T data = null;
                    // Void.class нельзя десериализовать (приватный конструктор) — считаем успех без тела
                    if (responseType == Void.class || (responseType != null && Void.TYPE.equals(responseType))) {
                        String msg = jsonObject.has("message") ? jsonObject.get("message").getAsString() : null;
                        return ApiResponse.success(null, msg, statusCode, jsonObject);
                    }
                    try {
                        if (responseType != null && !responseBody.isEmpty() && !responseBody.equals("{}")) {
                            if (jsonObject.has("items")) {
                                com.google.gson.JsonArray items = jsonObject.getAsJsonArray("items");
                                boolean rootIsEntity = jsonObject.has("id") || jsonObject.has("name");
                                if (java.util.List.class.equals(responseType) || (responseType != null && java.util.List.class.isAssignableFrom(responseType))) {
                                    data = gson.fromJson(items, responseType);
                                } else if (items.size() > 0 && !rootIsEntity) {
                                    // Ответ был массивом, обёрнут в items (нет id/name в корне) — берём первый элемент
                                    data = gson.fromJson(items.get(0), responseType);
                                } else {
                                    // Корень — одна сущность с полем items (например Shop)
                                    data = gson.fromJson(jsonObject, responseType);
                                }
                            } else if (jsonObject.has("success")) {
                                if (jsonObject.get("success").getAsBoolean() && jsonObject.has("data")) {
                                    data = gson.fromJson(jsonObject.get("data"), responseType);
                                } else {
                                    String error = jsonObject.has("error") ? jsonObject.get("error").getAsString() : "Unknown error";
                                    String detail = jsonObject.has("detail") ? jsonObject.get("detail").getAsString() : null;
                                    return ApiResponse.error(error, detail, statusCode, jsonObject);
                                }
                            } else if (jsonObject.has("data") && jsonObject.get("data").isJsonObject()) {
                                data = gson.fromJson(jsonObject.get("data"), responseType);
                            } else {
                                data = gson.fromJson(jsonObject, responseType);
                            }
                        }
                    } catch (com.google.gson.JsonSyntaxException | IllegalStateException e) {
                        logger.error("API parse failed for {} {} | expected type: {} | response body: {} | error: {}",
                                method, url, responseType != null ? responseType.getSimpleName() : "null",
                                responseBody, e.getMessage());
                        throw e;
                    }
                    
                    String message = jsonObject.has("message") ? jsonObject.get("message").getAsString() : null;
                    return ApiResponse.success(data, message, statusCode, jsonObject);
                } else {
                    // Error response
                    String error = "HTTP " + statusCode;
                    if (jsonObject.has("error")) {
                        com.google.gson.JsonElement errorElement = jsonObject.get("error");
                        if (errorElement.isJsonPrimitive()) {
                            error = errorElement.getAsString();
                        } else {
                            error = gson.toJson(errorElement);
                        }
                    }
                    
                    String detail = responseBody;
                    if (jsonObject.has("detail")) {
                        com.google.gson.JsonElement detailElement = jsonObject.get("detail");
                        if (detailElement.isJsonPrimitive()) {
                            detail = detailElement.getAsString();
                        } else {
                            detail = gson.toJson(detailElement);
                        }
                    }
                    
                    // Специальная обработка 422 Validation Error
                    if (statusCode == 422) {
                        // Извлекаем детали валидации из HTTPValidationError
                        if (jsonObject.has("detail")) {
                            try {
                                String validationDetails = gson.toJson(jsonObject.get("detail"));
                                detail = "Validation error: " + validationDetails;
                                logger.warn("Validation error (422): {}", validationDetails);
                            } catch (Exception e) {
                                logger.warn("Failed to parse validation error details", e);
                            }
                        }
                    }
                    
                    // Логирование ошибок клиента (4xx) кроме 422
                    if (statusCode >= 400 && statusCode < 500 && statusCode != 422) {
                        logger.warn("Client error {}: {} - {}", statusCode, error, detail);
                    }
                    
                    // Retry только для server errors (5xx) и если включен retry
                    if (statusCode >= 500 && config.isRetryEnabled() && attempts < config.getRetryMaxAttempts()) {
                        logger.warn("Server error {} on attempt {}, retrying in {}ms...", statusCode, attempts, delay);
                        try {
                            Thread.sleep(delay);
                            delay = (long) (delay * config.getRetryBackoffMultiplier());
                            continue;
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new ApiException("Request interrupted", e);
                        }
                    }
                    
                    return ApiResponse.error(error, detail, statusCode, jsonObject);
                }
            } catch (IOException e) {
                // Network error - retry если включен retry
                if (config.isRetryEnabled() && attempts < config.getRetryMaxAttempts()) {
                    logger.warn("Network error on attempt {}, retrying in {}ms...", attempts, delay, e);
                    try {
                        Thread.sleep(delay);
                        delay = (long) (delay * config.getRetryBackoffMultiplier());
                        continue;
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new ApiException("Request interrupted", ie);
                    }
                }
                
                logger.error("Failed to execute API request", e);
                throw new ApiException("Network error: " + e.getMessage(), e);
            }
        }
        
        throw new ApiException("Request failed after " + attempts + " attempts", null);
    }
    
    /**
     * Закрывает HTTP клиент.
     */
    public void shutdown() {
        httpClient.dispatcher().executorService().shutdown();
        httpClient.connectionPool().evictAll();
    }
}
