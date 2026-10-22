package dev.sh3lby.craftsocial;

import dev.sh3lby.craftsocial.api.ApiClient;
import dev.sh3lby.craftsocial.async.AsyncTaskManager;
import dev.sh3lby.craftsocial.cache.CacheManager;
import dev.sh3lby.craftsocial.commands.*;
import dev.sh3lby.craftsocial.config.PluginConfig;
import dev.sh3lby.craftsocial.gui.ClanGui;
import dev.sh3lby.craftsocial.gui.ClanManagementGui;
import dev.sh3lby.craftsocial.gui.PartyManagementGui;
import dev.sh3lby.craftsocial.gui.ShopGui;
import dev.sh3lby.craftsocial.gui.ShopManagementGui;
import dev.sh3lby.craftsocial.handlers.*;
import dev.sh3lby.craftsocial.listeners.InventoryListener;
import dev.sh3lby.craftsocial.listeners.PlayerActivityListener;
import dev.sh3lby.craftsocial.listeners.PlayerJoinListener;
import dev.sh3lby.craftsocial.listeners.ShopChestListener;
import dev.sh3lby.craftsocial.listeners.ShopEditChatListener;
import dev.sh3lby.craftsocial.listeners.ShopFrameListener;
import dev.sh3lby.craftsocial.tasks.ClanPartyInventoryRefreshTask;
import dev.sh3lby.craftsocial.tasks.OnlineRewardTask;
import dev.sh3lby.craftsocial.storage.ClanInventoryManager;
import dev.sh3lby.craftsocial.storage.PartyInventoryManager;
import dev.sh3lby.craftsocial.storage.OnlineTimeTracker;
import dev.sh3lby.craftsocial.storage.PlayerActivityTracker;
import dev.sh3lby.craftsocial.storage.ShopInventoryManager;
import dev.sh3lby.craftsocial.utils.ErrorHandler;
import dev.sh3lby.craftsocial.utils.MessagePalette;
import dev.sh3lby.craftsocial.utils.PlayerIdMapper;
import dev.sh3lby.craftsocial.utils.WebhookService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Главный класс плагина CraftSocial.
 * Stateless плагин для Purpur 1.21.8 с интеграцией CraftCore API.
 */
public class CraftSocialPlugin extends JavaPlugin {
    private static final Logger logger = LoggerFactory.getLogger(CraftSocialPlugin.class);
    
    // Core components
    private PluginConfig config;
    private AsyncTaskManager asyncTaskManager;
    private ApiClient apiClient;
    private CacheManager cacheManager;
    private ErrorHandler errorHandler;
    private PlayerIdMapper playerIdMapper;
    
    // Handlers
    private PlayerHandler playerHandler;
    private EconomyHandler economyHandler;
    private ShopHandler shopHandler;
    private ClanHandler clanHandler;
    private PartyHandler partyHandler;
    // GUI
    private ShopGui shopGui;
    private ShopManagementGui shopManagementGui;
    private ClanGui clanGui;
    private ClanManagementGui clanManagementGui;
    private PartyManagementGui partyManagementGui;
    
    // Storage
    private PartyInventoryManager partyInventoryManager;
    private ClanInventoryManager clanInventoryManager;
    private ShopInventoryManager shopInventoryManager;
    private dev.sh3lby.craftsocial.storage.ClanInviteManager clanInviteManager;
    private dev.sh3lby.craftsocial.storage.PartyInviteManager partyInviteManager;
    private dev.sh3lby.craftsocial.storage.ShopLocationManager shopLocationManager;
    
    private ClanPartyInventoryRefreshTask clanPartyInventoryRefreshTask;
    private OnlineRewardTask onlineRewardTask;
    private OnlineTimeTracker onlineTimeTracker;
    private PlayerActivityTracker playerActivityTracker;
    private WebhookService webhookService;
    
    // Command Handlers
    private ShopCommandHandler shopCommandHandler;
    private ClanCommandHandler clanCommandHandler;
    private PartyCommandHandler partyCommandHandler;
private EconomyCommandHandler economyCommandHandler;
    private QuestCommandHandler questCommandHandler;
    private dev.sh3lby.craftsocial.commands.HoursTestCommandHandler hoursTestCommandHandler;
    private TextCommandHandler textCommandHandler;
    private TextRemoveCommandHandler textRemoveCommandHandler;
    
    @Override
    public void onEnable() {
        logger.info("CraftSocial plugin is starting...");
        
        try {
            // 1. Загрузка конфигурации
            config = new PluginConfig(this);
            logger.info("Configuration loaded");
            
            // 2. Инициализация AsyncTaskManager
            asyncTaskManager = new AsyncTaskManager(
                this,
                config.getAsyncThreadPoolSize(),
                config.getAsyncQueueSize()
            );
            logger.info("AsyncTaskManager initialized");
            
            // 3. Инициализация ErrorHandler
            errorHandler = new ErrorHandler(config);
            logger.info("ErrorHandler initialized");
            
            // 4. Инициализация CacheManager
            cacheManager = new CacheManager(config);
            logger.info("CacheManager initialized");
            
            // 5. Инициализация ApiClient
            apiClient = new ApiClient(config, asyncTaskManager);
            logger.info("ApiClient initialized");
            
            // 5.5. Инициализация PlayerIdMapper
            playerIdMapper = new PlayerIdMapper(apiClient, cacheManager);
            logger.info("PlayerIdMapper initialized");
            
            // 6. Инициализация handlers
            playerHandler = new PlayerHandler(apiClient, cacheManager);
            economyHandler = new EconomyHandler(apiClient);
            shopHandler = new ShopHandler(apiClient, cacheManager, this, playerIdMapper);
            clanHandler = new ClanHandler(apiClient, cacheManager);
            partyHandler = new PartyHandler(apiClient, cacheManager);
            logger.info("All handlers initialized");
            
            // 7. Инициализация storage
            partyInventoryManager = new PartyInventoryManager(this);
            clanInventoryManager = new ClanInventoryManager(clanHandler, this);
            shopInventoryManager = new ShopInventoryManager(this);
            clanInviteManager = new dev.sh3lby.craftsocial.storage.ClanInviteManager();
            partyInviteManager = new dev.sh3lby.craftsocial.storage.PartyInviteManager();
            shopLocationManager = new dev.sh3lby.craftsocial.storage.ShopLocationManager(this);
            shopHandler.setShopLocationManager(shopLocationManager);
            shopHandler.setShopInventoryManager(shopInventoryManager);
            onlineTimeTracker = new OnlineTimeTracker();
            playerActivityTracker = new PlayerActivityTracker();
            logger.info("Storage managers initialized");
            
            // 8. Инициализация GUI
            shopGui = new ShopGui(shopHandler, this);
            shopManagementGui = new ShopManagementGui(shopHandler, this);
            clanGui = new ClanGui(clanHandler, this);
            clanManagementGui = new ClanManagementGui(clanHandler, clanInventoryManager, this);
            partyManagementGui = new PartyManagementGui(partyHandler, partyInventoryManager, this);
            logger.info("All GUI initialized");
            
            // 8.5. Инициализация WebhookService (нужен для command handlers и online reward task)
            webhookService = new WebhookService(apiClient);
            
            // 9. Инициализация command handlers
            shopCommandHandler = new ShopCommandHandler(shopHandler, shopGui, shopManagementGui, playerIdMapper, shopLocationManager);
            clanCommandHandler = new ClanCommandHandler(clanHandler, clanGui, clanManagementGui, clanInviteManager, playerHandler, playerIdMapper, this);
            partyCommandHandler = new PartyCommandHandler(partyHandler, partyManagementGui, partyInventoryManager, partyInviteManager, playerHandler, playerIdMapper, this);
            economyCommandHandler = new EconomyCommandHandler(economyHandler, playerIdMapper);
            questCommandHandler = new QuestCommandHandler(apiClient, playerIdMapper);
            hoursTestCommandHandler = new dev.sh3lby.craftsocial.commands.HoursTestCommandHandler(
                this,
                onlineTimeTracker,
                playerIdMapper,
                webhookService,
                config
            );
            textCommandHandler = new TextCommandHandler();
            textRemoveCommandHandler = new TextRemoveCommandHandler();
            logger.info("All command handlers initialized");
            
            // 10. Регистрация команд
            getCommand("shop").setExecutor(shopCommandHandler);
            getCommand("shop").setTabCompleter(shopCommandHandler);
            getCommand("clan").setExecutor(clanCommandHandler);
            getCommand("clan").setTabCompleter(clanCommandHandler);
            getCommand("jsparty").setExecutor(partyCommandHandler);
            getCommand("jsparty").setTabCompleter(partyCommandHandler);
            getCommand("money").setExecutor(economyCommandHandler);
            getCommand("money").setTabCompleter(economyCommandHandler);
            getCommand("economy").setExecutor(economyCommandHandler);
            getCommand("economy").setTabCompleter(economyCommandHandler);
            getCommand("quest").setExecutor(questCommandHandler);
            getCommand("quest").setTabCompleter(questCommandHandler);
            getCommand("jshours").setExecutor(hoursTestCommandHandler);
            getCommand("jshours").setTabCompleter(hoursTestCommandHandler);
            getCommand("text").setExecutor(textCommandHandler);
            getCommand("text").setTabCompleter(textCommandHandler);
            getCommand("textremove").setExecutor(textRemoveCommandHandler);
            logger.info("All commands registered");
            
            // 11. Регистрация listeners
            getServer().getPluginManager().registerEvents(new InventoryListener(this), this);
            getServer().getPluginManager().registerEvents(
                new PlayerJoinListener(this, playerHandler, playerIdMapper, playerActivityTracker), this);
            getServer().getPluginManager().registerEvents(
                new dev.sh3lby.craftsocial.listeners.PlayerQuitListener(this, onlineTimeTracker, playerActivityTracker), this);
            getServer().getPluginManager().registerEvents(
                new PlayerActivityListener(playerActivityTracker), this);
            getServer().getPluginManager().registerEvents(
                new ShopChestListener(this, shopHandler, shopLocationManager, playerIdMapper), this);
            getServer().getPluginManager().registerEvents(
                new ShopFrameListener(shopHandler, shopLocationManager, playerIdMapper), this);
            getServer().getPluginManager().registerEvents(new ShopEditChatListener(this), this);
            logger.info("Listeners registered");
            
            // 12. Периодическое обновление открытых инвентарей клана/группы (каждые 2 с) — против дюпа
            clanPartyInventoryRefreshTask = new ClanPartyInventoryRefreshTask(this);
            clanPartyInventoryRefreshTask.runTaskTimer(this, 8L, 8L);
            
            // 13. Периодическая награда за онлайн (каждый час)
            onlineRewardTask = new OnlineRewardTask(
                this,
                onlineTimeTracker,
                playerActivityTracker,
                playerIdMapper,
                webhookService,
                config
            );
            // Час считается от входа каждого игрока; проверяем раз в секунду.
            onlineRewardTask.runTaskTimer(this, 20L, 20L);
            
            logger.info("CraftSocial plugin enabled successfully!");
            logger.info("API Base URL: {}", config.getApiBaseUrl());
            logger.info("Cache TTL: {} seconds", config.getCacheTtl());
            logger.info("Async Thread Pool Size: {}", config.getAsyncThreadPoolSize());
            
        } catch (Exception e) {
            logger.error("Failed to enable CraftSocial plugin", e);
            getServer().getPluginManager().disablePlugin(this);
        }
    }
    
    @Override
    public void onDisable() {
        logger.info("CraftSocial plugin is shutting down...");
        
        // Graceful shutdown
        if (asyncTaskManager != null) {
            asyncTaskManager.shutdown();
        }
        
        if (apiClient != null) {
            apiClient.shutdown();
        }
        
        if (cacheManager != null) {
            cacheManager.clear();
        }
        
        if (clanPartyInventoryRefreshTask != null) {
            clanPartyInventoryRefreshTask.cancel();
        }
        
        if (onlineRewardTask != null) {
            onlineRewardTask.cancel();
        }
        
        if (partyInventoryManager != null) {
            partyInventoryManager.saveAll();
        }

        if (shopInventoryManager != null) {
            shopInventoryManager.saveAll();
        }
        
        if (shopLocationManager != null) {
            shopLocationManager.saveAll();
        }
        
        logger.info("CraftSocial plugin disabled");
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("craftsocial") && 
            !command.getName().equalsIgnoreCase("js") && 
            !command.getName().equalsIgnoreCase("jsocial")) {
            return false;
        }
        
        if (args.length == 0) {
            MessagePalette.sendRaw(sender, "§cCraftSocial §f v1.0.0-SNAPSHOT");
            MessagePalette.sendRaw(sender, "§fИспользуйте: §6/craftsocial [reload|info]");
            return true;
        }
        
        switch (args[0].toLowerCase()) {
            case "reload":
                if (!sender.hasPermission("craftsocial.reload")) {
                    MessagePalette.sendRaw(sender, "§cУ вас нет прав для выполнения этой команды.");
                    return true;
                }
                
                try {
                    config.loadConfig();
                    MessagePalette.sendRaw(sender, "§6Конфигурация перезагружена!");
                    logger.info("Configuration reloaded by {}", sender.getName());
                } catch (Exception e) {
                    MessagePalette.sendRaw(sender, "§cОшибка при перезагрузке конфигурации: §f" + e.getMessage());
                    logger.error("Failed to reload configuration", e);
                }
                return true;
                
            case "info":
                if (!sender.hasPermission("craftsocial.admin")) {
                    MessagePalette.sendRaw(sender, "§cУ вас нет прав для выполнения этой команды.");
                    return true;
                }
                
                MessagePalette.sendRaw(sender, "§c=== CraftSocial Info ===");
                MessagePalette.sendRaw(sender, "§fAPI Base URL: §6" + config.getApiBaseUrl());
                MessagePalette.sendRaw(sender, "§fCache: §6" + (config.isCacheEnabled() ? "Включен (TTL: " + config.getCacheTtl() + "s)" : "Отключен"));
                MessagePalette.sendRaw(sender, "§fAsync Tasks: §6" + (asyncTaskManager != null ? asyncTaskManager.getActiveTasksCount() : "N/A"));
                MessagePalette.sendRaw(sender, "§fCRM Status: " + (errorHandler != null && errorHandler.isCrmAvailable() ? "§6Доступна" : "§cНедоступна"));
                if (cacheManager != null) {
                    MessagePalette.sendRaw(sender, "§f" + cacheManager.getStats());
                }
                return true;
                
            default:
                MessagePalette.sendRaw(sender, "§cНеизвестная команда. Используйте: §6/craftsocial [reload|info]");
                return true;
        }
    }
    
    // Getters for handlers (для использования в других классах)
    public PluginConfig getPluginConfig() {
        return config;
    }
    
    public AsyncTaskManager getAsyncTaskManager() {
        return asyncTaskManager;
    }
    
    public ApiClient getApiClient() {
        return apiClient;
    }
    
    public CacheManager getCacheManager() {
        return cacheManager;
    }
    
    public ErrorHandler getErrorHandler() {
        return errorHandler;
    }
    
    public PlayerHandler getPlayerHandler() {
        return playerHandler;
    }
    
    public EconomyHandler getEconomyHandler() {
        return economyHandler;
    }
    
    public ShopHandler getShopHandler() {
        return shopHandler;
    }
    
    public ClanHandler getClanHandler() {
        return clanHandler;
    }
    
    public PartyHandler getPartyHandler() {
        return partyHandler;
    }
    
public PartyInventoryManager getPartyInventoryManager() {
        return partyInventoryManager;
    }
    
    public ClanInventoryManager getClanInventoryManager() {
        return clanInventoryManager;
    }
    
    public dev.sh3lby.craftsocial.storage.ShopLocationManager getShopLocationManager() {
        return shopLocationManager;
    }

    public ShopInventoryManager getShopInventoryManager() {
        return shopInventoryManager;
    }
    
    public ShopGui getShopGui() {
        return shopGui;
    }
    
    public PlayerIdMapper getPlayerIdMapper() {
        return playerIdMapper;
    }
    
    public OnlineTimeTracker getOnlineTimeTracker() {
        return onlineTimeTracker;
    }

    public PlayerActivityTracker getPlayerActivityTracker() {
        return playerActivityTracker;
    }
    
    public WebhookService getWebhookService() {
        return webhookService;
    }
}
