package dev.sh3lby.craftsocial.storage;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Трекер последней активности игрока в памяти.
 */
public class PlayerActivityTracker {
    private static class Entry {
        long lastActivityMillis;
    }

    private final Map<UUID, Entry> entries = new ConcurrentHashMap<>();

    public void onJoin(UUID uuid) {
        markActivity(uuid);
    }

    public void onQuit(UUID uuid) {
        entries.remove(uuid);
    }

    public void markActivity(UUID uuid) {
        if (uuid == null) {
            return;
        }
        Entry entry = entries.computeIfAbsent(uuid, ignored -> new Entry());
        entry.lastActivityMillis = System.currentTimeMillis();
    }

    public boolean isAfk(UUID uuid, long timeoutMillis) {
        return getIdleMillis(uuid) >= timeoutMillis;
    }

    public long getIdleMillis(UUID uuid) {
        Entry entry = entries.get(uuid);
        if (entry == null) {
            return Long.MAX_VALUE;
        }
        return Math.max(0L, System.currentTimeMillis() - entry.lastActivityMillis);
    }
}
