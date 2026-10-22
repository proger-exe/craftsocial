package dev.sh3lby.craftsocial.handlers;

import dev.sh3lby.craftsocial.api.ApiClient;
import dev.sh3lby.craftsocial.cache.CacheManager;
import dev.sh3lby.craftsocial.models.Shop;
import dev.sh3lby.craftsocial.models.ShopItem;
import dev.sh3lby.craftsocial.models.ShopItemsFullSyncResponse;
import dev.sh3lby.craftsocial.models.ShopLookupResult;
import dev.sh3lby.craftsocial.models.ShopPurchaseResponse;
import dev.sh3lby.craftsocial.storage.ShopInventoryManager;
import dev.sh3lby.craftsocial.storage.ShopLocationManager;
import dev.sh3lby.craftsocial.utils.ItemNbtSupport;
import dev.sh3lby.craftsocial.utils.MessagePalette;
import dev.sh3lby.craftsocial.utils.PlayerIdMapper;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;
import org.slf4j.Logger;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Обработчик магазинов.
 * Реализует поток покупки согласно MAX.md:
 * 1. Валидация (через создание транзакции)
 * 2. Списание (через транзакцию)
 * 3. Выдача предмета
 */
public class ShopHandler {
    private static final Logger logger = LoggerFactory.getLogger(ShopHandler.class);
    private static final long ORPHAN_RECHECK_DELAY_TICKS = 40L;
    private static final String SHOP_TEXT_TAG = "craftsocial_shop_label";
    
    /** Минимальная цена товара (1 вайши). */
    public static final long MIN_SHOP_PRICE = 1L;
    
    private final ApiClient apiClient;
    private final CacheManager cacheManager;
    private final Plugin plugin;
    private final PlayerIdMapper playerIdMapper;
    private final ConcurrentHashMap<Integer, CompletableFuture<Boolean>> pendingChestSyncs = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, OrphanShopState> orphanShopStates = new ConcurrentHashMap<>();
    private ShopLocationManager shopLocationManager;
    private ShopInventoryManager shopInventoryManager;

    private static final class OrphanShopState {
        private volatile long first404AtMillis;
        private volatile int hits;
        private volatile boolean verificationScheduled;
    }

    public ShopHandler(ApiClient apiClient, CacheManager cacheManager, Plugin plugin, PlayerIdMapper playerIdMapper) {
        this.apiClient = apiClient;
        this.cacheManager = cacheManager;
        this.plugin = plugin;
        this.playerIdMapper = playerIdMapper;
    }
    
    /**
     * Устанавливает ShopLocationManager (вызывается после инициализации).
     */
    public void setShopLocationManager(ShopLocationManager shopLocationManager) {
        this.shopLocationManager = shopLocationManager;
    }

    public void setShopInventoryManager(ShopInventoryManager shopInventoryManager) {
        this.shopInventoryManager = shopInventoryManager;
    }
    
    /**
     * Инвалидирует кеш магазина (чтобы при следующем getShop данные подтянулись с API).
     */
    public void invalidateShopCache(int shopId) {
        cacheManager.invalidate("shop:" + shopId);
    }
    
    /**
     * Получает магазин по ID (с кешированием).
     */
    public CompletableFuture<Shop> getShop(int shopId, long playerId) {
        return getShopLookup(shopId, playerId).thenApply(ShopLookupResult::getShop);
    }

    /**
     * Получает магазин по ID с метаданными ответа API.
     */
    public CompletableFuture<ShopLookupResult> getShopLookup(int shopId, long playerId) {
        Shop cached = cacheManager.get("shop:" + shopId, Shop.class);
        if (cached != null) {
            return CompletableFuture.completedFuture(rememberFoundShop(shopId, cached, 200));
        }
        return fetchShopLookup(shopId, playerId, true);
    }
    
    /**
     * Создает магазин.
     * В теле отправляются name и owner_id (player_id владельца).
     */
    public CompletableFuture<Shop> createShop(String name, long playerId) {
        java.util.Map<String, Object> request = new java.util.HashMap<>();
        request.put("name", name);
        request.put("owner_id", playerId);
        
        return apiClient.post("/user/shops", request, Shop.class, playerId)
            .thenApply(response -> {
                if (response.isSuccess() && response.getData() != null) {
                    // Инвалидируем кеш списка магазинов
                    cacheManager.invalidate("shops:list:" + playerId);
                    return response.getData();
                } else {
                    logger.warn("Failed to create shop: {}", response.getError());
                    return null;
                }
            });
    }
    
    /**
     * Создает магазин с привязкой к локации сундука.
     * После создания спавнит text_display с названием магазина впереди лицевой части сундука.
     */
    public CompletableFuture<Shop> createShopWithLocation(String name, long playerId, org.bukkit.Location chestLocation) {
        return createShop(name, playerId).thenApply(shop -> {
            if (shop != null && chestLocation != null) {
                logger.info("Shop {} created at location {}", shop.getId(), chestLocation);
                // Спавн text_display на главном потоке
                org.bukkit.Location loc = chestLocation.clone();
                String shopName = shop.getName();
                plugin.getServer().getScheduler().runTask(plugin, () -> spawnShopNameDisplay(loc, shopName));
            }
            return shop;
        });
    }

    /**
     * Спавнит TextDisplay с названием магазина впереди лицевой части сундука.
     * Поворачивает дисплей туда же, куда смотрит сундук.
     */
    private void spawnShopNameDisplay(org.bukkit.Location chestLocation, String shopName) {
        org.bukkit.block.Block block = chestLocation.getWorld().getBlockAt(chestLocation);
        if (!(block.getBlockData() instanceof Directional)) {
            return;
        }
        org.bukkit.block.BlockFace facing = ((Directional) block.getBlockData()).getFacing();
        // Смещаем текст наружу перед лицевой стороной сундука,
        // чтобы он не спавнился внутри блока.
        Vector dir = new Vector(facing.getModX(), facing.getModY(), facing.getModZ()).multiply(0.72);
        org.bukkit.Location displayLoc = chestLocation.clone().add(0.5, 0.5, 0.5).add(dir);
        // Поворот дисплея как у сундука: yaw/pitch по направлению лица сундука
        float yaw = blockFaceToYaw(facing);
        float pitch = blockFaceToPitch(facing);
        displayLoc.setYaw(yaw);
        displayLoc.setPitch(pitch);
        org.bukkit.entity.Entity entity = chestLocation.getWorld().spawnEntity(displayLoc, org.bukkit.entity.EntityType.TEXT_DISPLAY);
        entity.setRotation(yaw, pitch);
        if (entity instanceof TextDisplay) {
            TextDisplay textDisplay = (TextDisplay) entity;
            Component text = LegacyComponentSerializer.legacySection().deserialize(MessagePalette.paint("§c" + shopName));
            textDisplay.text(text);
            textDisplay.setInvulnerable(true);
            textDisplay.addScoreboardTag(SHOP_TEXT_TAG);
        }
    }

    private static float blockFaceToYaw(org.bukkit.block.BlockFace face) {
        switch (face) {
            case SOUTH: return 0f;
            case SOUTH_WEST: return 45f;
            case WEST: return 90f;
            case NORTH_WEST: return 135f;
            case NORTH: return 180f;
            case NORTH_EAST: return -135f;
            case EAST: return -90f;
            case SOUTH_EAST: return -45f;
            default: return 0f;
        }
    }

    private static float blockFaceToPitch(org.bukkit.block.BlockFace face) {
        switch (face) {
            case UP: return -90f;
            case DOWN: return 90f;
            default: return 0f;
        }
    }
    
    /**
     * Обновляет магазин.
     */
    public CompletableFuture<Boolean> updateShop(int shopId, String name, long playerId) {
        java.util.Map<String, Object> request = new java.util.HashMap<>();
        request.put("name", name);
        
        return apiClient.put("/user/shops/" + shopId, request, Shop.class, playerId)
            .thenApply(response -> {
                if (response.isSuccess()) {
                    // Инвалидируем кеш магазина и списка
                    cacheManager.invalidate("shop:" + shopId);
                    cacheManager.invalidate("shops:list:" + playerId);
                    return true;
                } else {
                    logger.warn("Failed to update shop {}: {}", shopId, response.getError());
                    return false;
                }
            });
    }
    
    /**
     * Удаляет магазин.
     */
    public CompletableFuture<Boolean> deleteShop(int shopId, long playerId) {
        return apiClient.delete("/user/shops/" + shopId, Void.class, playerId)
            .thenApply(response -> {
                if (response.isSuccess()) {
                    forceRemoveShopFromWorld(shopId);
                    cacheManager.invalidate("shops:list:" + playerId);
                    return true;
                } else {
                    logger.warn("Failed to delete shop {}: {}", shopId, response.getError());
                    return false;
                }
            });
    }
    
    /**
     * Legacy-метод (без total_count): total_count = quantity.
     */
    public CompletableFuture<ShopItem> addItem(int shopId, String itemId, long price, int quantity, long playerId) {
        return addItem(shopId, itemId, price, quantity, quantity, playerId);
    }

    /**
     * Добавляет товар в магазин по новой модели:
     * price = цена за 1 ед., quantity = размер лота, total_count = остаток.
     * itemNbt — опционально (пустая строка / null = без NBT).
     */
    public CompletableFuture<ShopItem> addItem(int shopId, String itemId, long price, int quantity, int totalCount, long playerId) {
        return addItem(shopId, itemId, null, price, quantity, totalCount, playerId);
    }

    /**
     * Добавляет товар в магазин с опциональным item_nbt.
     */
    public CompletableFuture<ShopItem> addItem(int shopId, String itemId, String itemNbt, long price, int quantity, int totalCount, long playerId) {
        if (price < MIN_SHOP_PRICE || quantity <= 0 || totalCount < 0) {
            return CompletableFuture.completedFuture(null);
        }
        java.util.Map<String, Object> request = new java.util.HashMap<>();
        request.put("item_id", itemId);
        if (itemNbt != null && !itemNbt.isBlank()) {
            request.put("item_nbt", itemNbt);
        }
        request.put("price", price);
        request.put("quantity", quantity);
        request.put("total_count", totalCount);
        return apiClient.post("/user/shops/" + shopId + "/items", request, ShopItem.class, playerId)
            .thenApply(response -> {
                if (response.isSuccess() && response.getData() != null) {
                    cacheManager.invalidate("shop:" + shopId);
                    return response.getData();
                }
                logger.warn("Failed to add item to shop {}: {}", shopId, response.getError());
                return null;
            });
    }
    
    /**
     * Legacy-метод: обновляет цену и (опционально) quantity.
     */
    public CompletableFuture<Boolean> updateItem(int shopId, int itemId, long price, Integer quantity, long playerId) {
        return updateItem(shopId, itemId, price, quantity, null, null, playerId);
    }

    /**
     * Обновляет товар (цена, quantity, total_count). Без смены item_nbt.
     */
    public CompletableFuture<Boolean> updateItem(int shopId, int itemId, long price, Integer quantity, Integer totalCount, long playerId) {
        return updateItem(shopId, itemId, price, quantity, totalCount, null, playerId);
    }

    /**
     * Обновляет товар в магазине (любые поля опциональны).
     * itemNbt — опционально; если передан непустой, отправляется в API (смена NBT позиции).
     */
    public CompletableFuture<Boolean> updateItem(int shopId, int itemId, Long price, Integer quantity, Integer totalCount, String itemNbt, long playerId) {
        if (price != null && price < MIN_SHOP_PRICE) {
            return CompletableFuture.completedFuture(false);
        }
        if (quantity != null && quantity <= 0) {
            return CompletableFuture.completedFuture(false);
        }
        if (totalCount != null && totalCount < 0) {
            return CompletableFuture.completedFuture(false);
        }
        java.util.Map<String, Object> request = new java.util.HashMap<>();
        if (price != null) request.put("price", price);
        if (quantity != null) request.put("quantity", quantity);
        if (totalCount != null) request.put("total_count", totalCount);
        if (itemNbt != null && !itemNbt.isBlank()) request.put("item_nbt", itemNbt);
        if (request.isEmpty()) {
            return CompletableFuture.completedFuture(false);
        }
        
        return apiClient.put("/user/shops/" + shopId + "/items/" + itemId, request, Void.class, playerId)
            .thenApply(response -> {
                if (response.isSuccess()) {
                    cacheManager.invalidate("shop:" + shopId);
                    return true;
                } else {
                    logger.warn("Failed to update item {} in shop {}: {}", itemId, shopId, response.getError());
                    return false;
                }
            });
    }
    
    /**
     * Удаляет товар из магазина.
     */
    public CompletableFuture<Boolean> deleteItem(int shopId, int itemId, long playerId) {
        return apiClient.delete("/user/shops/" + shopId + "/items/" + itemId, Void.class, playerId)
            .thenApply(response -> {
                if (response.isSuccess()) {
                    // Инвалидируем кеш магазина
                    cacheManager.invalidate("shop:" + shopId);
                    return true;
                } else {
                    logger.warn("Failed to delete item {} from shop {}: {}", itemId, shopId, response.getError());
                    return false;
                }
            });
    }

    /**
     * Полная синхронизация товаров магазина с сервером.
     * Входной список items считается источником истины (upsert + удаление отсутствующих).
     */
    public CompletableFuture<ShopItemsFullSyncResponse> fullSyncItems(int shopId, java.util.List<java.util.Map<String, Object>> items, long playerId) {
        java.util.Map<String, Object> request = new java.util.HashMap<>();
        request.put("items", items != null ? items : java.util.Collections.emptyList());
        return apiClient.put("/user/shops/" + shopId + "/items/full-sync", request, ShopItemsFullSyncResponse.class, playerId)
            .thenApply(response -> {
                if (response.isSuccess() && response.getData() != null) {
                    cacheManager.invalidate("shop:" + shopId);
                    return response.getData();
                }
                logger.warn("Failed to full-sync shop {} items: {}", shopId, response.getError());
                return null;
            });
    }
    
    /**
     * Получает список магазинов (с кешированием).
     */
    @SuppressWarnings("unchecked")
    public CompletableFuture<java.util.List<Shop>> getShops(Integer ownerId, long playerId, int page, int perPage) {
        String cacheKey = "shops:list:" + playerId + ":" + ownerId + ":" + page;
        
        return cacheManager.getOrCompute(cacheKey, () -> {
            StringBuilder endpoint = new StringBuilder("/user/shops?page=").append(page).append("&per_page=").append(perPage);
            if (ownerId != null) {
                endpoint.append("&owner_id=").append(ownerId);
            }
            
            return apiClient.get(endpoint.toString(), java.util.List.class, playerId)
                .thenApply(response -> {
                    if (response.isSuccess() && response.getData() != null) {
                        // Десериализация списка магазинов
                        if (response.getRawJson() != null) {
                            return dev.sh3lby.craftsocial.utils.GsonUtils.deserializeList(
                                response.getRawJson(), 
                                Shop.class
                            );
                        }
                        return new java.util.ArrayList<>();
                    } else {
                        logger.warn("Failed to get shops: {}", response.getError());
                        return null;
                    }
                });
        }, java.util.List.class).thenApply(data -> (java.util.List<Shop>) data);
    }
    
    /**
     * Покупает товар в магазине через API (POST /user/shops/{shop_id}/items/{item_id}/buy).
     * Перед вызовом API проверяет наличие товара и достаточность остатка.
     * См. SHOP_PURCHASE.md: проверка баланса, списание, зачисление владельцу и остаток — на стороне API.
     */
    public CompletableFuture<Boolean> purchaseItem(Player player, int shopId, int itemId, int quantity) {
        long playerId = playerIdMapper.getPlayerIdSync(player);
        if (quantity <= 0) {
            sendMessageSync(player, "§cТовара нет в наличии или недостаточно остатка.");
            return CompletableFuture.completedFuture(false);
        }
        return getShopLookup(shopId, playerId)
            .thenCompose(result -> {
                Shop shop = result.getShop();
                if (shop == null || shop.getItems() == null) {
                    sendMessageSync(player, "§cТовара нет в наличии или недостаточно остатка.");
                    return CompletableFuture.<Boolean>completedFuture(false);
                }
                ShopItem item = shop.getItems().stream()
                    .filter(i -> i.getId() == itemId)
                    .findFirst()
                    .orElse(null);
                if (item == null || item.getTotalCount() < quantity) {
                    sendMessageSync(player, "§cТовара нет в наличии или недостаточно остатка.");
                    return CompletableFuture.completedFuture(false);
                }
                Map<String, Object> body = Map.of("quantity", quantity);
                return apiClient.post("/user/shops/" + shopId + "/items/" + itemId + "/buy", body, ShopPurchaseResponse.class, playerId)
                    .thenCompose(response -> {
                        if (response.isSuccess() && response.getData() != null) {
                            ShopPurchaseResponse data = response.getData();
                            // NBT: из ответа API или из позиции магазина (если API не отдаёт item_nbt)
                            String nbt = (data.getItemNbt() != null && !data.getItemNbt().isBlank())
                                ? data.getItemNbt() : item.getItemNbt();
                            return giveItemAsync(player, data.getItemId(), data.getQuantity(), nbt)
                                .thenApply(success -> {
                                    if (!success) {
                                        logger.error("Failed to give item to player {} after successful purchase", playerId);
                                        sendMessageSync(player, "§cОшибка выдачи предмета. Обратитесь к администратору.");
                                        return false;
                                    }
                                    if (shopInventoryManager != null) {
                                        shopInventoryManager.consumePurchasedItems(
                                            shopId,
                                            data.getItemId(),
                                            nbt,
                                            data.getQuantity()
                                        );
                                    }
                                    cacheManager.invalidate("shop:" + shopId);
                                    sendMessageSync(player, "§6Покупка успешна! §fКуплено x§6" + data.getQuantity() + "§f, списано §6" + data.getTotalPaid() + " вайши.");
                                    return true;
                                });
                        } else {
                            String message = formatPurchaseError(response.getStatusCode(), response.getError(), response.getDetail());
                            sendMessageSync(player, message);
                            return CompletableFuture.completedFuture(false);
                        }
                    });
            })
            .exceptionally(error -> {
                logger.warn("Purchase failed for player {}: {}", playerId, error.getMessage());
                sendMessageSync(player, "§cОшибка покупки. Попробуйте позже или обратитесь к администратору.");
                return false;
            });
    }

    private String formatPurchaseError(int statusCode, String error, String detail) {
        switch (statusCode) {
            case 402:
                return "Недостаточно средств";
            case 400:
                return detail != null && !detail.isEmpty() ? detail : "Недостаточно товара в магазине";
            case 404:
                return "Магазин или товар не найден";
            case 422:
                return "Ошибка валидации" + (detail != null && !detail.isEmpty() ? ": " + detail : "");
            default:
                return error != null ? error : "Ошибка покупки";
        }
    }
    
    /**
     * Выдаёт предмет игроку асинхронно (синхронизируется с главным потоком).
     * itemNbt — опционально; если не пустой, предмет собирается через ItemNbtSupport.
     */
    private CompletableFuture<Boolean> giveItemAsync(Player player, String itemId, int quantity, String itemNbt) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        
        // Синхронизируемся с главным потоком для выдачи предмета
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            try {
                ItemStack item;
                if (itemNbt != null && !itemNbt.isBlank()) {
                    item = ItemNbtSupport.fromNbtString(itemId, itemNbt, quantity);
                } else {
                    Material material = Material.matchMaterial(itemId);
                    if (material == null) {
                        logger.warn("Unknown material: {}", itemId);
                        future.complete(false);
                        return;
                    }
                    item = new ItemStack(material, quantity);
                }
                if (item == null || item.getType() == Material.AIR) {
                    logger.warn("Could not create item for item_id={}", itemId);
                    future.complete(false);
                    return;
                }
                player.getInventory().addItem(item);
                future.complete(true);
            } catch (Exception e) {
                logger.error("Error giving item to player", e);
                future.complete(false);
            }
        });
        
        return future;
    }
    
    public CompletableFuture<Boolean> awaitPendingChestSync(int shopId) {
        CompletableFuture<Boolean> pending = pendingChestSyncs.get(shopId);
        return pending != null ? pending : CompletableFuture.completedFuture(true);
    }

    public CompletableFuture<Boolean> enqueueChestSync(int shopId, Supplier<CompletableFuture<Boolean>> supplier) {
        return pendingChestSyncs.compute(shopId, (id, previous) -> {
            CompletableFuture<Boolean> base = previous != null
                ? previous.handle((result, error) -> Boolean.TRUE)
                : CompletableFuture.completedFuture(true);
            CompletableFuture<Boolean> next = base.thenCompose(ignored -> supplier.get());
            next.whenComplete((result, error) -> pendingChestSyncs.remove(id, next));
            return next;
        });
    }

    /**
     * Полная синхронизация локального JSON-инвентаря магазина в API.
     * Группирует содержимое по (item_id, item_nbt) и делает full-sync.
     */
    public CompletableFuture<Boolean> syncInventorySnapshot(int shopId, long playerId, ItemStack[] contents) {
        java.util.Map<String, Integer> chestCounts = new java.util.HashMap<>();
        for (ItemStack item : contents) {
            if (item == null || item.getType() == Material.AIR) {
                continue;
            }
            String itemId = item.getType().getKey().toString();
            String nbt = ItemNbtSupport.toNbtString(item);
            String slotKey = ItemNbtSupport.slotKey(itemId, nbt);
            chestCounts.merge(slotKey, item.getAmount(), Integer::sum);
        }

        return getShopLookup(shopId, playerId).thenCompose(result -> {
            Shop shop = result.getShop();
            if (shop == null) {
                return CompletableFuture.completedFuture(false);
            }

            java.util.List<ShopItem> existingItems =
                shop.getItems() != null ? shop.getItems() : java.util.Collections.emptyList();
            java.util.Map<String, ShopItem> existingBySlotKey = new java.util.HashMap<>();
            for (ShopItem si : existingItems) {
                String key = ItemNbtSupport.slotKey(si.getItemId(), si.getItemNbt());
                existingBySlotKey.put(key, si);
            }

            long defaultPrice = ((dev.sh3lby.craftsocial.CraftSocialPlugin) plugin).getPluginConfig().getShopDefaultItemPrice();
            int defaultLotSize = 1;
            java.util.List<java.util.Map<String, Object>> payloadItems = new java.util.ArrayList<>();
            for (java.util.Map.Entry<String, Integer> entry : chestCounts.entrySet()) {
                String slotKey = entry.getKey();
                int totalCount = entry.getValue();
                int sep = slotKey.indexOf('\0');
                String itemId = sep >= 0 ? slotKey.substring(0, sep) : slotKey;
                String itemNbt = sep >= 0 && sep + 1 < slotKey.length() ? slotKey.substring(sep + 1) : "";
                ShopItem existing = existingBySlotKey.get(slotKey);
                java.util.Map<String, Object> row = new java.util.HashMap<>();
                row.put("item_id", itemId);
                if (itemNbt != null && !itemNbt.isEmpty()) {
                    row.put("item_nbt", itemNbt);
                }
                row.put("price", existing != null ? existing.getPrice() : defaultPrice);
                row.put("quantity", existing != null ? existing.getQuantity() : defaultLotSize);
                row.put("total_count", totalCount);
                payloadItems.add(row);
            }

            return fullSyncItems(shopId, payloadItems, playerId).thenApply(sync -> {
                if (sync != null) {
                    logger.info("Shop {} full-sync done. created={}, updated={}, deleted={}, total={}",
                        shopId, sync.getCreated(), sync.getUpdated(), sync.getDeleted(), sync.getTotal());
                    return true;
                }
                logger.warn("Shop {} full-sync failed", shopId);
                return false;
            });
        }).exceptionally(error -> {
            logger.error("Failed to sync shop {} from snapshot", shopId, error);
            return false;
        });
    }

    private CompletableFuture<ShopLookupResult> fetchShopLookup(int shopId, long playerId, boolean allowOrphanFallback) {
        return apiClient.get("/user/shops/" + shopId, Shop.class, playerId)
            .thenCompose(response -> {
                if (response.isSuccess() && response.getData() != null) {
                    ShopLookupResult result = rememberFoundShop(shopId, response.getData(), response.getStatusCode());
                    return CompletableFuture.completedFuture(result);
                }

                if (response.getStatusCode() == 404) {
                    logger.warn("Shop {} returned API 404", shopId);
                    return handleApiNotFound(shopId, playerId, allowOrphanFallback, response.getStatusCode());
                }

                clearOrphanState(shopId);
                logger.warn("Failed to get shop {}: {} (status={})", shopId, response.getError(), response.getStatusCode());
                return CompletableFuture.completedFuture(
                    ShopLookupResult.failed(shopId, response.getStatusCode(), response.getStatusCode() >= 500 || response.getStatusCode() <= 0)
                );
            });
    }

    private ShopLookupResult rememberFoundShop(int shopId, Shop shop, int statusCode) {
        clearOrphanState(shopId);
        cacheManager.put("shop:" + shopId, shop);
        if (shopLocationManager != null) {
            shopLocationManager.setShopOwner(shopId, shop.getOwnerId());
        }
        return ShopLookupResult.success(shopId, shop, statusCode);
    }

    private CompletableFuture<ShopLookupResult> handleApiNotFound(int shopId, long playerId, boolean allowOrphanFallback, int statusCode) {
        if (!allowOrphanFallback) {
            return CompletableFuture.completedFuture(ShopLookupResult.apiNotFound(shopId, statusCode));
        }

        OrphanShopState state = orphanShopStates.compute(shopId, (id, current) -> {
            OrphanShopState target = current != null ? current : new OrphanShopState();
            target.hits += 1;
            if (target.first404AtMillis == 0L) {
                target.first404AtMillis = System.currentTimeMillis();
            }
            return target;
        });

        if (state.hits == 1) {
            scheduleOrphanVerification(shopId, playerId);
        }

        return CompletableFuture.completedFuture(ShopLookupResult.apiNotFound(shopId, statusCode));
    }

    private void scheduleOrphanVerification(int shopId, long playerId) {
        OrphanShopState state = orphanShopStates.get(shopId);
        if (state == null || state.verificationScheduled) {
            return;
        }
        state.verificationScheduled = true;
        plugin.getServer().getScheduler().runTaskLaterAsynchronously(plugin, () -> {
            fetchShopLookup(shopId, playerId, false)
                .thenAccept(result -> {
                    if (result.isApiNotFound()) {
                        OrphanShopState current = orphanShopStates.get(shopId);
                        if (current != null && current.hits >= 1) {
                            long elapsedMillis = System.currentTimeMillis() - current.first404AtMillis;
                            logger.warn("Shop {} confirmed orphaned after repeated API 404 ({} ms); cleaning local state", shopId, elapsedMillis);
                            plugin.getServer().getScheduler().runTask(plugin, () -> forceRemoveShopFromWorld(shopId));
                        }
                    } else if (result.isFound()) {
                        logger.info("Shop {} recovered after initial API 404; orphan cleanup cancelled", shopId);
                    } else {
                        logger.info("Shop {} orphan cleanup cancelled because verification returned status {}", shopId, result.getStatusCode());
                    }
                })
                .exceptionally(error -> {
                    logger.warn("Shop {} orphan verification failed", shopId, error);
                    clearOrphanState(shopId);
                    return null;
                });
        }, ORPHAN_RECHECK_DELAY_TICKS);
    }

    private void clearOrphanState(int shopId) {
        orphanShopStates.remove(shopId);
    }

    public void forceRemoveShopFromWorld(int shopId) {
        if (!Bukkit.isPrimaryThread()) {
            plugin.getServer().getScheduler().runTask(plugin, () -> forceRemoveShopFromWorld(shopId));
            return;
        }
        Location location = shopLocationManager != null ? shopLocationManager.getShopLocation(shopId) : null;
        clearOrphanState(shopId);
        pendingChestSyncs.remove(shopId);
        cacheManager.invalidate("shop:" + shopId);
        if (shopLocationManager != null) {
            shopLocationManager.removeShopLocation(shopId);
        }
        if (shopInventoryManager != null) {
            shopInventoryManager.removeInventory(shopId);
        }
        closeOpenShopInventories(shopId);
        removeShopTextDisplays(location);
    }

    private void closeOpenShopInventories(int shopId) {
        for (Player online : Bukkit.getOnlinePlayers()) {
            String title = online.getOpenInventory().getTitle();
            if (title == null) {
                continue;
            }
            if (title.startsWith("Магазин: " + shopId + ":")
                || title.startsWith("Инвентарь магазина: " + shopId + ":")
                || title.startsWith("Управление магазином: " + shopId + ":")) {
                online.closeInventory();
                MessagePalette.sendRaw(online, "§cМагазин был удалён из системы.");
            }
        }
    }

    private void removeShopTextDisplays(Location location) {
        if (location == null || location.getWorld() == null) {
            return;
        }
        for (Entity entity : location.getWorld().getNearbyEntities(location.clone().add(0.5, 0.5, 0.5), 1.5, 1.5, 1.5)) {
            if (entity instanceof TextDisplay && entity.getScoreboardTags().contains(SHOP_TEXT_TAG)) {
                entity.remove();
            }
        }
    }

    public Shop getCachedShop(int shopId) {
        return cacheManager.get("shop:" + shopId, Shop.class);
    }
    
    /**
     * Отправляет сообщение игроку (синхронизируется с главным потоком).
     */
    private void sendMessageSync(Player player, String message) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            MessagePalette.send(player, message);
        });
    }
    
}
