package dev.sh3lby.craftsocial.api;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.sh3lby.craftsocial.config.PluginConfig;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Обменивает client_id/client_secret на короткоживущий service JWT
 * через POST /integrations/auth/token и кэширует его до истечения срока.
 *
 * Токен нужен только для запросов от имени системного игрока (777000),
 * то есть /admin/* endpoints. Без client_secret возвращает null.
 */
public class ServiceTokenProvider {
    private static final Logger logger = LoggerFactory.getLogger(ServiceTokenProvider.class);
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final PluginConfig config;
    private final OkHttpClient httpClient;
    private final Object lock = new Object();

    private volatile String cachedToken;
    private volatile long expiresAtMillis;

    public ServiceTokenProvider(PluginConfig config, OkHttpClient httpClient) {
        this.config = config;
        this.httpClient = httpClient;
    }

    /**
     * Возвращает валидный service token, обменивая credentials при необходимости.
     * Возвращает null если client_secret не сконфигурирован или обмен провалился.
     */
    public String getToken() {
        String clientSecret = config.getServiceAuthClientSecret();
        if (clientSecret == null || clientSecret.isEmpty()) {
            return null;
        }
        long now = System.currentTimeMillis();
        String token = cachedToken;
        if (token != null && now < expiresAtMillis) {
            return token;
        }
        synchronized (lock) {
            if (cachedToken != null && System.currentTimeMillis() < expiresAtMillis) {
                return cachedToken;
            }
            return fetchAndCache();
        }
    }

    private String fetchAndCache() {
        String clientId = config.getServiceAuthClientId();
        String clientSecret = config.getServiceAuthClientSecret();
        String url = config.getApiBaseUrl() + "/integrations/auth/token";
        String body = String.format(
            "{\"client_id\":\"%s\",\"client_secret\":\"%s\"}",
            escape(clientId), escape(clientSecret));

        Request request = new Request.Builder()
            .url(url)
            .header("Content-Type", "application/json")
            .post(RequestBody.create(body, JSON))
            .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                logger.warn("Service token exchange failed: HTTP {} — {}", response.code(), responseBody);
                cachedToken = null;
                expiresAtMillis = 0L;
                return null;
            }
            JsonObject obj = JsonParser.parseString(responseBody).getAsJsonObject();
            String token = obj.get("access_token").getAsString();
            int expiresIn = obj.has("expires_in") ? obj.get("expires_in").getAsInt() : 3600;
            long ttlMs = Math.max(0L,
                (expiresIn - config.getServiceAuthTokenRefreshBufferSeconds()) * 1000L);
            cachedToken = token;
            expiresAtMillis = System.currentTimeMillis() + ttlMs;
            logger.info("Service token obtained, valid for {}s (refresh buffer {}s)",
                expiresIn, config.getServiceAuthTokenRefreshBufferSeconds());
            return token;
        } catch (Exception e) {
            logger.error("Service token exchange threw", e);
            cachedToken = null;
            expiresAtMillis = 0L;
            return null;
        }
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
