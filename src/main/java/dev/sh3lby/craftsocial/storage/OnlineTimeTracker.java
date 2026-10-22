package dev.sh3lby.craftsocial.storage;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Трекинг онлайна игроков в памяти.
 * Считает полные часы отдельно для каждой игровой сессии.
 */
public class OnlineTimeTracker {
    static final long HOUR_MILLIS = 3_600_000L;

    private static class Entry {
        final long joinedAtMillis;
        int consecutiveHours;

        Entry(long joinedAtMillis) {
            this.joinedAtMillis = joinedAtMillis;
        }
    }

    private final Map<UUID, Entry> entries = new ConcurrentHashMap<>();

    public void onJoin(UUID uuid) {
        entries.put(uuid, new Entry(System.currentTimeMillis()));
    }

    public void onQuit(UUID uuid) {
        entries.remove(uuid);
    }

    /**
     * Возвращает текущее значение счётчика часов и увеличивает его на 1.
     * 0 означает первый полных час в текущей сессии.
     */
    public int getAndIncrementHours(UUID uuid) {
        Entry entry = entries.computeIfAbsent(uuid, ignored -> new Entry(System.currentTimeMillis()));
        int current = entry.consecutiveHours;
        entry.consecutiveHours = current + 1;
        return current;
    }

    /** Возвращает новый полный час сессии или 0, если следующий час ещё не наступил. */
    public int claimCompletedHour(UUID uuid) {
        return claimCompletedHour(uuid, System.currentTimeMillis());
    }

    int claimCompletedHour(UUID uuid, long nowMillis) {
        Entry entry = entries.computeIfAbsent(uuid, ignored -> new Entry(nowMillis));
        int completedHours = (int) ((nowMillis - entry.joinedAtMillis) / HOUR_MILLIS);
        if (completedHours <= entry.consecutiveHours) {
            return 0;
        }
        return ++entry.consecutiveHours;
    }

    /**
     * Возвращает текущее значение счётчика часов без изменения.
     * Если игрок не отслеживается, возвращает 0.
     */
    public int getConsecutiveHours(UUID uuid) {
        Entry entry = entries.get(uuid);
        return entry != null ? entry.consecutiveHours : 0;
    }

    public boolean isOnline(UUID uuid) {
        return entries.containsKey(uuid);
    }
}
