package dev.sh3lby.craftsocial.models;

/**
 * Результат чтения магазина из API с сохранением статуса ответа.
 */
public class ShopLookupResult {
    private final int shopId;
    private final Shop shop;
    private final int statusCode;
    private final boolean apiNotFound;
    private final boolean transportOrServerFailure;

    public ShopLookupResult(int shopId, Shop shop, int statusCode, boolean apiNotFound, boolean transportOrServerFailure) {
        this.shopId = shopId;
        this.shop = shop;
        this.statusCode = statusCode;
        this.apiNotFound = apiNotFound;
        this.transportOrServerFailure = transportOrServerFailure;
    }

    public static ShopLookupResult success(int shopId, Shop shop, int statusCode) {
        return new ShopLookupResult(shopId, shop, statusCode, false, false);
    }

    public static ShopLookupResult apiNotFound(int shopId, int statusCode) {
        return new ShopLookupResult(shopId, null, statusCode, true, false);
    }

    public static ShopLookupResult failed(int shopId, int statusCode, boolean transportOrServerFailure) {
        return new ShopLookupResult(shopId, null, statusCode, false, transportOrServerFailure);
    }

    public int getShopId() {
        return shopId;
    }

    public Shop getShop() {
        return shop;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public boolean isFound() {
        return shop != null;
    }

    public boolean isApiNotFound() {
        return apiNotFound;
    }

    public boolean isTransportOrServerFailure() {
        return transportOrServerFailure;
    }
}
