package com.tripbler.backend.crypto.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import com.tripbler.backend.crypto.config.CryptoCacheProperties;
import com.tripbler.backend.crypto.domain.CryptoCoin;
import com.tripbler.backend.crypto.dto.*;

class CryptoCacheConcurrencyTest {
    private final Clock clock = mock(Clock.class);
    private final CryptoResponseCache cache = new CryptoResponseCache(new CryptoCacheProperties(), clock);
    private final Instant initial = Instant.parse("2026-09-30T00:00:00Z");

    private Object load(boolean history, Supplier<Object> supplier) {
        if (history) {
            return cache.getHistory(CryptoCoin.ETH, "KRW", CryptoHistoryPeriod.ONE_YEAR,
                () -> (CryptoHistoryResponse) supplier.get());
        }
        return cache.getPrice(CryptoCoin.ETH, "KRW", () -> (CryptoPriceResponse) supplier.get());
    }

    private Object response(boolean history, Instant time) {
        return history
            ? new CryptoHistoryResponse("ETH", "KRW", "1Y", 365, List.of(), time)
            : new CryptoPriceResponse("ETH", "KRW", BigDecimal.ONE, time);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void initialAndExpiredConcurrentRequestsLoadOnce(boolean history) throws Exception {
        var calls = new AtomicInteger();
        for (int round = 0; round < 2; round++) {
            Instant now = initial.plusSeconds(round * (history ? 900 : 60));
            when(clock.instant()).thenReturn(now);
            Object expected = response(history, now);
            var results = concurrent(() -> load(history, () -> {
                calls.incrementAndGet();
                return expected;
            }));
            for (Object result : results) assertSame(expected, result);
            assertEquals(round + 1, calls.get());
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void failedConcurrentLoadCanBeRetriedAndDoesNotPoisonCache(boolean history) throws Exception {
        when(clock.instant()).thenReturn(initial);
        var calls = new AtomicInteger();
        var failure = new IllegalStateException("provider failure");
        Object expected = response(history, initial);
        var results = concurrent(() -> {
            try {
                return load(history, () -> {
                    if (calls.incrementAndGet() == 1) throw failure;
                    return expected;
                });
            } catch (IllegalStateException e) {
                return e;
            }
        });
        assertEquals(1, results.stream().filter(r -> r == failure).count());
        assertEquals(7, results.stream().filter(r -> r == expected).count());
        assertEquals(2, calls.get());
    }

    @Test
    void slowHistoryDoesNotBlockPriceOrOtherCoinOrPeriod() throws Exception {
        when(clock.instant()).thenReturn(initial);
        var started = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            var slow = pool.submit(() -> cache.getHistory(CryptoCoin.BTC, "KRW", CryptoHistoryPeriod.SEVEN_DAYS, () -> {
                started.countDown();
                try { assertTrue(release.await(5, TimeUnit.SECONDS)); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new RuntimeException(e); }
                return new CryptoHistoryResponse("BTC", "KRW", "7D", 7, List.of(), initial);
            }));
            assertTrue(started.await(5, TimeUnit.SECONDS));
            var other = pool.submit(() -> {
                cache.getPrice(CryptoCoin.BTC, "KRW", () -> new CryptoPriceResponse("BTC", "KRW", BigDecimal.ONE, initial));
                cache.getHistory(CryptoCoin.ETH, "KRW", CryptoHistoryPeriod.SEVEN_DAYS,
                    () -> new CryptoHistoryResponse("ETH", "KRW", "7D", 7, List.of(), initial));
                return cache.getHistory(CryptoCoin.BTC, "KRW", CryptoHistoryPeriod.ONE_YEAR,
                    () -> new CryptoHistoryResponse("BTC", "KRW", "1Y", 365, List.of(), initial));
            });
            assertEquals("1Y", other.get(3, TimeUnit.SECONDS).period());
            release.countDown();
            assertEquals("7D", slow.get(3, TimeUnit.SECONDS).period());
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }

    private List<Object> concurrent(Supplier<Object> task) throws Exception {
        var pool = Executors.newFixedThreadPool(8);
        var start = new CyclicBarrier(8);
        try {
            List<Future<Object>> futures = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                futures.add(pool.submit(() -> { start.await(5, TimeUnit.SECONDS); return task.get(); }));
            }
            List<Object> results = new ArrayList<>();
            for (var future : futures) results.add(future.get(5, TimeUnit.SECONDS));
            return results;
        } finally {
            pool.shutdownNow();
        }
    }
}
