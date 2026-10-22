package dev.sh3lby.craftsocial.api;

import com.google.gson.JsonObject;

/**
 * Обертка для ответов API.
 */
public class ApiResponse<T> {
    private final boolean success;
    private final T data;
    private final String message;
    private final String error;
    private final String detail;
    private final int statusCode;
    private final JsonObject rawJson;
    
    public ApiResponse(boolean success, T data, String message, String error, String detail, int statusCode, JsonObject rawJson) {
        this.success = success;
        this.data = data;
        this.message = message;
        this.error = error;
        this.detail = detail;
        this.statusCode = statusCode;
        this.rawJson = rawJson;
    }
    
    public static <T> ApiResponse<T> success(T data, String message, int statusCode, JsonObject rawJson) {
        return new ApiResponse<>(true, data, message, null, null, statusCode, rawJson);
    }
    
    public static <T> ApiResponse<T> error(String error, String detail, int statusCode, JsonObject rawJson) {
        return new ApiResponse<>(false, null, null, error, detail, statusCode, rawJson);
    }
    
    public boolean isSuccess() {
        return success;
    }
    
    public T getData() {
        return data;
    }
    
    public String getMessage() {
        return message;
    }
    
    public String getError() {
        return error;
    }
    
    public String getDetail() {
        return detail;
    }
    
    public int getStatusCode() {
        return statusCode;
    }
    
    public JsonObject getRawJson() {
        return rawJson;
    }
}
