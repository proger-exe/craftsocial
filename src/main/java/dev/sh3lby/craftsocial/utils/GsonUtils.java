package dev.sh3lby.craftsocial.utils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import dev.sh3lby.craftsocial.models.Clan;
import dev.sh3lby.craftsocial.models.ClanDeserializer;

import java.util.ArrayList;
import java.util.List;

/**
 * Утилиты для работы с Gson и десериализации списков.
 */
public class GsonUtils {
    private static final Gson gson = new Gson();
    private static final Gson clanGson = new GsonBuilder()
        .registerTypeAdapter(Clan.class, new ClanDeserializer())
        .create();
    
    /**
     * Десериализует список из JSON ответа API.
     */
    public static <T> List<T> deserializeList(JsonObject jsonObject, Class<T> itemClass) {
        List<T> result = new ArrayList<>();
        
        Gson deserializer = (itemClass == Clan.class) ? clanGson : gson;
        if (jsonObject.has("items")) {
            // Paginated response
            JsonArray itemsArray = jsonObject.getAsJsonArray("items");
            for (JsonElement element : itemsArray) {
                T item = deserializer.fromJson(element, itemClass);
                if (item != null) {
                    result.add(item);
                }
            }
        } else if (jsonObject.isJsonArray()) {
            // Direct array
            JsonArray array = jsonObject.getAsJsonArray();
            for (JsonElement element : array) {
                T item = deserializer.fromJson(element, itemClass);
                if (item != null) {
                    result.add(item);
                }
            }
        } else if (jsonObject.has("data") && jsonObject.get("data").isJsonArray()) {
            // Data field contains array
            JsonArray array = jsonObject.getAsJsonArray("data");
            for (JsonElement element : array) {
                T item = deserializer.fromJson(element, itemClass);
                if (item != null) {
                    result.add(item);
                }
            }
        }
        
        return result;
    }
    
    /**
     * Десериализует список из JSON ответа API с использованием TypeToken.
     */
    public static <T> List<T> deserializeList(JsonObject jsonObject, TypeToken<List<T>> typeToken) {
        List<T> result = new ArrayList<>();
        
        if (jsonObject.has("items")) {
            // Paginated response
            JsonArray itemsArray = jsonObject.getAsJsonArray("items");
            for (JsonElement element : itemsArray) {
                T item = gson.fromJson(element, typeToken.getType());
                if (item != null) {
                    result.add(item);
                }
            }
        } else if (jsonObject.isJsonArray()) {
            // Direct array
            result = gson.fromJson(jsonObject, typeToken.getType());
        } else if (jsonObject.has("data") && jsonObject.get("data").isJsonArray()) {
            // Data field contains array
            result = gson.fromJson(jsonObject.get("data"), typeToken.getType());
        }
        
        return result;
    }
}
