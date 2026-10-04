package cgeo.geocaching.connector.pgc;

import cgeo.geocaching.utils.Log;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Known rate-limited Project-GC API endpoints.
 * Each constant carries its own limit config and tracks its own sliding window,
 * so callers pass the enum value explicitly instead of relying on path matching.
 *
 * Add a new constant whenever a new endpoint with a known limit is used.
 */
enum PgcEndpoint {

    CHALLENGE_CHECKER_GCCODE_CACHED(10, 60_000),
    VGPS_ENUMERATE(10, 60_000),
    VGPS_RETRIEVE(10, 60_000),
    // add further endpoints with confirmed limits here, e.g.:
    // CHALLENGE_CHECKER_GCCODE_CHECK(?, 60_000),
    // CHALLENGE_CHECKER_TAG_CACHED(?, 60_000),
    ;

    private static final long REACTIVE_BACKOFF_MS = 60_000;

    private final long maxCalls;
    private final long windowMs;
    private final Deque<Long> callLog = new ArrayDeque<>();
    private long backoffUntil = 0;

    PgcEndpoint(final long maxCalls, final long windowMs) {
        this.maxCalls = maxCalls;
        this.windowMs = windowMs;
    }

    /**
     * Blocks the calling thread until this endpoint's rate limit permits another call,
     * then records the call and returns.
     */
    void acquire() {
        while (true) {
            final long sleepMs = checkAndRecord();
            if (sleepMs <= 0) {
                return;
            }
            Log.d("PgcEndpoint " + name() + ": throttling for " + sleepMs + " ms");
            try {
                Thread.sleep(sleepMs);
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    /** Call when an HTTP 429 is received. Clears the window and triggers a full-window backoff. */
    synchronized void on429() {
        backoffUntil = System.currentTimeMillis() + REACTIVE_BACKOFF_MS;
        callLog.clear();
        Log.w("PgcEndpoint " + name() + ": HTTP 429 received; backing off " + REACTIVE_BACKOFF_MS + " ms");
    }

    /**
     * Checks whether a call may proceed now. If yes, records the timestamp and returns 0.
     * If no, returns the number of milliseconds to wait before retrying.
     */
    private synchronized long checkAndRecord() {
        final long now = System.currentTimeMillis();

        if (now < backoffUntil) {
            return backoffUntil - now;
        }

        Long oldest = callLog.peekFirst();
        while (oldest != null && now - oldest >= windowMs) {
            callLog.pollFirst();
            oldest = callLog.peekFirst();
        }

        if (oldest == null || callLog.size() < maxCalls) {
            callLog.addLast(now);
            return 0;
        }

        return windowMs - (now - oldest) + 1;
    }
}
