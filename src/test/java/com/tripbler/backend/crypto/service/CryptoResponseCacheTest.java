package com.tripbler.backend.crypto.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import com.tripbler.backend.crypto.domain.CryptoCoin;
import java.time.*;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import com.tripbler.backend.common.exception.BusinessException;
import com.tripbler.backend.common.exception.ErrorCode;
import com.tripbler.backend.crypto.client.CryptoMarketClient;
import com.tripbler.backend.crypto.config.CryptoCacheProperties;
import com.tripbler.backend.crypto.dto.*;

class CryptoResponseCacheTest {
    private final MutableClock clock = new MutableClock();
    private final CryptoCacheProperties properties = new CryptoCacheProperties();
    private final CryptoResponseCache cache = new CryptoResponseCache(properties, clock);
    private final CryptoMarketClient client = mock(CryptoMarketClient.class);
    private final CryptoService service = new CryptoService(client, cache);

    private CryptoPriceResponse price() {
        return new CryptoPriceResponse("BTC", "KRW", BigDecimal.TEN, clock.instant());
    }

    private CryptoHistoryResponse history(CryptoHistoryPeriod period) {
        return new CryptoHistoryResponse("BTC", "KRW", period.code(), period.days(), List.of(), clock.instant());
    }

    @Test
    void currentPriceExpiresAtSixtySecondsAndPreservesFetchedAtOnHits() {
        when(client.getCurrentPrice(CryptoCoin.BTC, "KRW")).thenAnswer(invocation -> price());
        var first = service.getCurrentPrice("bitcoin", "KRW");
        clock.advance(Duration.ofSeconds(59));
        assertSame(first, service.getCurrentPrice(" Bitcoin ", " krw "));
        verify(client, times(1)).getCurrentPrice(CryptoCoin.BTC, "KRW");
        clock.advance(Duration.ofSeconds(1));
        var refreshed = service.getCurrentPrice("bitcoin", "KRW");
        assertNotSame(first, refreshed);
        assertEquals(first.fetchedAt().plusSeconds(60), refreshed.fetchedAt());
        verify(client, times(2)).getCurrentPrice(CryptoCoin.BTC, "KRW");
    }

    @Test
    void historiesUseSeparatePeriodKeysAndFifteenMinuteExpiry() {
        when(client.getHistoricalPrices(eq(CryptoCoin.BTC), eq("KRW"), any()))
            .thenAnswer(invocation -> history(invocation.getArgument(2)));
        var week = service.getHistoricalPrices("bitcoin", "KRW", "7D");
        var year = service.getHistoricalPrices("bitcoin", "KRW", "1Y");
        clock.advance(Duration.ofMinutes(14));
        assertSame(week, service.getHistoricalPrices(" Bitcoin ", " krw ", " 7d "));
        assertSame(year, service.getHistoricalPrices("bitcoin", "KRW", "1Y"));
        clock.advance(Duration.ofMinutes(1));
        assertNotSame(week, service.getHistoricalPrices("bitcoin", "KRW", "7D"));
        verify(client, times(2)).getHistoricalPrices(CryptoCoin.BTC, "KRW", CryptoHistoryPeriod.SEVEN_DAYS);
        verify(client, times(1)).getHistoricalPrices(CryptoCoin.BTC, "KRW", CryptoHistoryPeriod.ONE_YEAR);
    }

    @Test
    void failedLoadAndFailedRefreshAreNotReturnedAsCachedSuccess() {
        var failure = new BusinessException(ErrorCode.CRYPTO_PROVIDER_UNAVAILABLE);
        when(client.getCurrentPrice(CryptoCoin.BTC, "KRW"))
            .thenThrow(failure).thenAnswer(invocation -> price())
            .thenThrow(failure).thenAnswer(invocation -> price());
        assertThrows(BusinessException.class, () -> service.getCurrentPrice("bitcoin", "KRW"));
        var first = service.getCurrentPrice("bitcoin", "KRW");
        clock.advance(Duration.ofSeconds(60));
        assertThrows(BusinessException.class, () -> service.getCurrentPrice("bitcoin", "KRW"));
        assertNotSame(first, service.getCurrentPrice("bitcoin", "KRW"));
        verify(client, times(4)).getCurrentPrice(CryptoCoin.BTC, "KRW");
    }

    @Test
    void sameKeyConcurrentRequestsShareOneProviderCall() throws Exception {
        var executor = Executors.newFixedThreadPool(8);
        var ready = new CountDownLatch(8);
        var start = new CountDownLatch(1);
        var loading = new CountDownLatch(1);
        var finish = new CountDownLatch(1);
        var calls = new AtomicInteger();
        var expected = price();
        try {
            List<Future<CryptoPriceResponse>> results = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                results.add(executor.submit(() -> {
                    ready.countDown();
                    assertTrue(start.await(5, TimeUnit.SECONDS));
                    return cache.getPrice(CryptoCoin.BTC, "KRW", () -> {
                        calls.incrementAndGet();
                        loading.countDown();
                        try { assertTrue(finish.await(5, TimeUnit.SECONDS)); }
                        catch (InterruptedException e) { throw new RuntimeException(e); }
                        return expected;
                    });
                }));
            }
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            assertTrue(loading.await(5, TimeUnit.SECONDS));
            finish.countDown();
            for (var result : results) assertSame(expected, result.get(5, TimeUnit.SECONDS));
            assertEquals(1, calls.get());
        } finally {
            start.countDown();
            finish.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void customTtlsAreIndependentAndPositive() {
        properties.setPriceTtl(Duration.ofSeconds(30));
        properties.setHistoryTtl(Duration.ofHours(1));
        var configured = new CryptoResponseCache(properties, clock);
        var p = configured.getPrice(CryptoCoin.BTC, "KRW", this::price);
        var h = configured.getHistory(CryptoCoin.BTC, "KRW", CryptoHistoryPeriod.ONE_YEAR,
            () -> history(CryptoHistoryPeriod.ONE_YEAR));
        clock.advance(Duration.ofSeconds(30));
        assertNotSame(p, configured.getPrice(CryptoCoin.BTC, "KRW", this::price));
        assertSame(h, configured.getHistory(CryptoCoin.BTC, "KRW", CryptoHistoryPeriod.ONE_YEAR,
            () -> history(CryptoHistoryPeriod.ONE_YEAR)));
        clock.advance(Duration.ofSeconds(3570));
        assertNotSame(h, configured.getHistory(CryptoCoin.BTC, "KRW", CryptoHistoryPeriod.ONE_YEAR,
            () -> history(CryptoHistoryPeriod.ONE_YEAR)));
        assertThrows(IllegalArgumentException.class, () -> properties.setPriceTtl(Duration.ZERO));
        assertThrows(IllegalArgumentException.class, () -> properties.setHistoryTtl(Duration.ofSeconds(-1)));
    }

    private static class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-09-30T00:00:00Z");
        void advance(Duration duration) { now = now.plus(duration); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}
