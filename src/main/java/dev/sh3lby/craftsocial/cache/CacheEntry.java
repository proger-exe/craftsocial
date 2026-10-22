package dev.sh3lby.craftsocial.cache;

import java.time.Instant;

/**
 * Запись кеша с временной меткой.
 */
public class CacheEntry<T> {
    private final T data;
    private final Instant timestamp;
    private final long ttlSeconds;
    
    public CacheEntry(T data, long ttlSeconds) {
        this.data = data;
        this.timestamp = Instant.now();
        this.ttlSeconds = ttlSeconds;
    }
    
    public T getData() {
        return data;
    }
    
    public Instant getTimestamp() {
        return timestamp;
    }
    
    public boolean isExpired() {
        return Instant.now().isAfter(timestamp.plusSeconds(ttlSeconds));
    }
    
    public long getTtlSeconds() {
        return ttlSeconds;
    }
}
