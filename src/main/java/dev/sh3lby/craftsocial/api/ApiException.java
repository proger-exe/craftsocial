package dev.sh3lby.craftsocial.api;

/**
 * Исключение для ошибок API.
 */
public class ApiException extends Exception {
    private final int statusCode;
    private final String error;
    private final String detail;
    
    public ApiException(int statusCode, String error, String detail) {
        super("API Error: " + error + " (Status: " + statusCode + ")");
        this.statusCode = statusCode;
        this.error = error;
        this.detail = detail;
    }
    
    public ApiException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = 0;
        this.error = message;
        this.detail = cause != null ? cause.getMessage() : null;
    }
    
    public int getStatusCode() {
        return statusCode;
    }
    
    public String getError() {
        return error;
    }
    
    public String getDetail() {
        return detail;
    }
    
    public boolean isClientError() {
        return statusCode >= 400 && statusCode < 500;
    }
    
    public boolean isServerError() {
        return statusCode >= 500;
    }
}
