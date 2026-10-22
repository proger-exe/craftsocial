package dev.sh3lby.craftsocial.storage;

import java.util.UUID;

public class OnlineTimeTrackerTest {
    public static void main(String[] args) {
        OnlineTimeTracker tracker = new OnlineTimeTracker();
        UUID player = UUID.randomUUID();
        long joinedAt = 1_000L;

        tracker.claimCompletedHour(player, joinedAt);
        assert tracker.claimCompletedHour(player, joinedAt + OnlineTimeTracker.HOUR_MILLIS - 1) == 0;
        assert tracker.claimCompletedHour(player, joinedAt + OnlineTimeTracker.HOUR_MILLIS) == 1;
        assert tracker.claimCompletedHour(player, joinedAt + OnlineTimeTracker.HOUR_MILLIS) == 0;
        assert tracker.claimCompletedHour(player, joinedAt + 2 * OnlineTimeTracker.HOUR_MILLIS) == 2;
    }
}
