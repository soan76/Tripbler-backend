package com.tripbler.backend.common.cache;

import static org.junit.jupiter.api.Assertions.*;
import java.net.SocketTimeoutException;
import java.time.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class TimeoutFallbackCacheTest {
    private static final class MutableClock extends Clock {
        Instant now = Instant.parse("2026-10-07T00:00:00Z");
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return this; }
        public Instant instant() { return now; }
    }
    private record Value(int price, Instant fetchedAt, boolean stale) {
        Value asStale() { return new Value(price, fetchedAt, true); }
    }
    private RuntimeException timeout() { return new RuntimeException(new SocketTimeoutException()); }

    @Test void expiredSuccessSurvivesTimeoutWithoutExtendingTimestampOrFallbackDeadline() {
        var clock = new MutableClock();
        var cache = new TimeoutFallbackCache<String, Value>(clock, Duration.ofSeconds(60), Duration.ofMinutes(10), 8);
        var original = new Value(100, clock.instant(), false);
        cache.get("BTC", () -> original, Value::asStale);
        clock.now = clock.now.plusSeconds(60);
        AtomicInteger calls = new AtomicInteger();
        var stale = cache.get("BTC", () -> { calls.incrementAndGet(); throw timeout(); }, Value::asStale);
        assertTrue(stale.stale());
        assertEquals(original.fetchedAt(), stale.fetchedAt());
        assertEquals(stale, cache.get("BTC", () -> { calls.incrementAndGet(); throw timeout(); }, Value::asStale));
        assertEquals(1, calls.get());
        clock.now = original.fetchedAt().plusSeconds(660);
        assertThrows(RuntimeException.class, () -> cache.get("BTC", () -> { throw timeout(); }, Value::asStale));
    }

    @Test void nonTimeoutAndColdTimeoutDoNotReturnAnotherKeysData() {
        var clock = new MutableClock();
        var cache = new TimeoutFallbackCache<String, Value>(clock, Duration.ofSeconds(60), Duration.ofMinutes(10), 8);
        cache.get("BTC", () -> new Value(100, clock.instant(), false), Value::asStale);
        clock.now = clock.now.plusSeconds(60);
        assertThrows(IllegalArgumentException.class, () -> cache.get("BTC", () -> { throw new IllegalArgumentException(); }, Value::asStale));
        assertThrows(RuntimeException.class, () -> cache.get("ETH", () -> { throw timeout(); }, Value::asStale));
        var recovered = cache.get("BTC", () -> new Value(200, clock.instant(), false), Value::asStale);
        assertEquals(200, recovered.price());
        assertFalse(recovered.stale());
    }
}
