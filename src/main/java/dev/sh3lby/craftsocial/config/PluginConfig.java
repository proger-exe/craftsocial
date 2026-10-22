package dev.sh3lby.craftsocial.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;

/**
 * Конфигурация плагина.
 */
public class PluginConfig {
    private final Plugin plugin;
    private FileConfiguration config;
    
    // API Configuration
    private String apiBaseUrl;
    private int apiTimeout;
    private boolean retryEnabled;
    private int retryMaxAttempts;
    private long retryDelay;
    private double retryBackoffMultiplier;
    private String serviceAuthClientId;
    private String serviceAuthClientSecret;
    private int serviceAuthTokenRefreshBufferSeconds;
    
    // Cache Configuration
    private boolean cacheEnabled;
    private int cacheTtl;
    private int cacheMaxSize;
    
    // Async Configuration
    private int asyncThreadPoolSize;
    private int asyncQueueSize;
    
    // Error Handling
    private boolean readOnlyOnFailure;
    private boolean disableEconomyOnFailure;
    private boolean disableSocialOnFailure;
    private boolean logAllErrors;
    
    // Rate Limiting
    private boolean rateLimitingEnabled;
    private int rateLimitRequestsPerSecond;
    private int rateLimitBurstSize;
    
    // Features
    private boolean playerManagementEnabled;
    private boolean economyEnabled;
    private boolean shopsEnabled;
    private boolean clansEnabled;
    private boolean partyEnabled;
    
    // Shop Configuration
    private long shopDefaultItemPrice;
    
    // Online rewards
    private boolean onlineRewardsEnabled;
    private int onlineMinReward;
    private int onlineMaxReward;
    private double onlineMultiplierBase;
    private double onlineMultiplierStep;
    private boolean onlineRewardsAfkProtectionEnabled;
    private int onlineRewardsAfkTimeoutSeconds;
    
    // Debug
    private boolean debug;
    
    public PluginConfig(Plugin plugin) {
        this.plugin = plugin;
        loadConfig();
    }
    
    public void loadConfig() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        config = plugin.getConfig();
        
        // API Configuration
        apiBaseUrl = config.getString("api.base-url", "https://api.example.com");
        apiTimeout = config.getInt("api.timeout", 30);
        retryEnabled = config.getBoolean("api.retry.enabled", true);
        retryMaxAttempts = config.getInt("api.retry.max-attempts", 3);
        retryDelay = config.getLong("api.retry.delay", 1000);
        retryBackoffMultiplier = config.getDouble("api.retry.backoff-multiplier", 2.0);
        serviceAuthClientId = config.getString("api.service-auth.client-id", "craftsocial");
        serviceAuthClientSecret = config.getString("api.service-auth.client-secret", "");
        serviceAuthTokenRefreshBufferSeconds = config.getInt("api.service-auth.token-refresh-buffer-seconds", 60);
        
        // Cache Configuration
        cacheEnabled = config.getBoolean("cache.enabled", true);
        cacheTtl = config.getInt("cache.ttl", 20);
        cacheMaxSize = config.getInt("cache.max-size", 1000);
        
        // Async Configuration
        asyncThreadPoolSize = config.getInt("async.thread-pool-size", 10);
        asyncQueueSize = config.getInt("async.queue-size", 100);
        
        // Error Handling
        readOnlyOnFailure = config.getBoolean("error-handling.read-only-on-failure", true);
        disableEconomyOnFailure = config.getBoolean("error-handling.disable-economy-on-failure", true);
        disableSocialOnFailure = config.getBoolean("error-handling.disable-social-on-failure", true);
        logAllErrors = config.getBoolean("error-handling.log-all-errors", true);
        
        // Rate Limiting
        rateLimitingEnabled = config.getBoolean("rate-limiting.enabled", true);
        rateLimitRequestsPerSecond = config.getInt("rate-limiting.requests-per-second", 10);
        rateLimitBurstSize = config.getInt("rate-limiting.burst-size", 20);
        
        // Features
        playerManagementEnabled = config.getBoolean("features.player-management", true);
        economyEnabled = config.getBoolean("features.economy", true);
        shopsEnabled = config.getBoolean("features.shops", true);
        clansEnabled = config.getBoolean("features.clans", true);
        partyEnabled = config.getBoolean("features.party", true);
        
        // Shop Configuration
        shopDefaultItemPrice = config.getLong("shops.default-item-price", 100);
        
        // Online rewards
        onlineRewardsEnabled = config.getBoolean("online-rewards.enabled", true);
        onlineMinReward = config.getInt("online-rewards.min-reward", 10);
        onlineMaxReward = config.getInt("online-rewards.max-reward", 30);
        onlineMultiplierBase = config.getDouble("online-rewards.multiplier-base", 1.0);
        onlineMultiplierStep = config.getDouble("online-rewards.multiplier-step", 0.5);
        onlineRewardsAfkProtectionEnabled = config.getBoolean("online-rewards.afk-protection.enabled", true);
        onlineRewardsAfkTimeoutSeconds = config.getInt("online-rewards.afk-protection.timeout-seconds", 300);
        
        // Debug
        debug = config.getBoolean("debug", false);
    }
    
    // Getters
    public String getApiBaseUrl() { return apiBaseUrl; }
    public int getApiTimeout() { return apiTimeout; }
    public boolean isRetryEnabled() { return retryEnabled; }
    public int getRetryMaxAttempts() { return retryMaxAttempts; }
    public long getRetryDelay() { return retryDelay; }
    public double getRetryBackoffMultiplier() { return retryBackoffMultiplier; }
    public String getServiceAuthClientId() { return serviceAuthClientId; }
    public String getServiceAuthClientSecret() { return serviceAuthClientSecret; }
    public int getServiceAuthTokenRefreshBufferSeconds() { return serviceAuthTokenRefreshBufferSeconds; }
    
    public boolean isCacheEnabled() { return cacheEnabled; }
    public int getCacheTtl() { return cacheTtl; }
    public int getCacheMaxSize() { return cacheMaxSize; }
    
    public int getAsyncThreadPoolSize() { return asyncThreadPoolSize; }
    public int getAsyncQueueSize() { return asyncQueueSize; }
    
    public boolean isReadOnlyOnFailure() { return readOnlyOnFailure; }
    public boolean isDisableEconomyOnFailure() { return disableEconomyOnFailure; }
    public boolean isDisableSocialOnFailure() { return disableSocialOnFailure; }
    public boolean isLogAllErrors() { return logAllErrors; }
    
    public boolean isRateLimitingEnabled() { return rateLimitingEnabled; }
    public int getRateLimitRequestsPerSecond() { return rateLimitRequestsPerSecond; }
    public int getRateLimitBurstSize() { return rateLimitBurstSize; }
    
    public boolean isPlayerManagementEnabled() { return playerManagementEnabled; }
    public boolean isEconomyEnabled() { return economyEnabled; }
    public boolean isShopsEnabled() { return shopsEnabled; }
    public boolean isClansEnabled() { return clansEnabled; }
    public boolean isPartyEnabled() { return partyEnabled; }
    
    public long getShopDefaultItemPrice() { return shopDefaultItemPrice; }
    
    public boolean isOnlineRewardsEnabled() { return onlineRewardsEnabled; }
    public int getOnlineMinReward() { return onlineMinReward; }
    public int getOnlineMaxReward() { return onlineMaxReward; }
    public double getOnlineMultiplierBase() { return onlineMultiplierBase; }
    public double getOnlineMultiplierStep() { return onlineMultiplierStep; }
    public boolean isOnlineRewardsAfkProtectionEnabled() { return onlineRewardsAfkProtectionEnabled; }
    public int getOnlineRewardsAfkTimeoutSeconds() { return onlineRewardsAfkTimeoutSeconds; }
    
    public boolean isDebug() { return debug; }
}
