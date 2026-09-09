package technology.renatus.routepulse.shared.security;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class PublicRequestThrottle {

    private static final int MAXIMUM_TRACKED_KEYS = 10_000;

    private final Map<String, AttemptBucket> buckets =
            new ConcurrentHashMap<>();

    public boolean tryAcquire(
            String action,
            String clientKey,
            int limit,
            Duration window) {

        String safeClientKey = clientKey == null
                ? "unknown"
                : clientKey;

        Instant now = Instant.now();

        String bucketKey = action + ":" + safeClientKey;

        if (!buckets.containsKey(bucketKey)
                && buckets.size()
                        >= MAXIMUM_TRACKED_KEYS) {

            removeOldBuckets(now);
        }

        /*
         * Bound memory use even during a burst of new client
         * addresses. New clients share a conservative overflow
         * bucket until older entries age out.
         */
        if (!buckets.containsKey(bucketKey)
                && buckets.size()
                        >= MAXIMUM_TRACKED_KEYS) {

            bucketKey = action + ":overflow";
        }

        AttemptBucket bucket = buckets.computeIfAbsent(
                bucketKey,
                ignored -> new AttemptBucket()
        );

        boolean acquired = bucket.tryAcquire(
                now,
                limit,
                window
        );

        return acquired;
    }

    private void removeOldBuckets(Instant now) {

        Instant cutoff = now.minus(Duration.ofHours(2));

        buckets.entrySet().removeIf(entry ->
                entry.getValue().lastAttemptBefore(cutoff)
        );
    }

    private static final class AttemptBucket {

        private final Deque<Instant> attempts =
                new ArrayDeque<>();

        private synchronized boolean tryAcquire(
                Instant now,
                int limit,
                Duration window) {

            Instant cutoff = now.minus(window);

            while (!attempts.isEmpty()
                    && attempts.peekFirst()
                            .isBefore(cutoff)) {

                attempts.removeFirst();
            }

            if (attempts.size() >= limit) {
                return false;
            }

            attempts.addLast(now);
            return true;
        }

        private synchronized boolean lastAttemptBefore(
                Instant cutoff) {

            return attempts.isEmpty()
                    || attempts.peekLast().isBefore(cutoff);
        }
    }
}
