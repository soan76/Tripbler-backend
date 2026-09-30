package com.tripbler.backend.crypto.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.tripbler.backend.crypto.config.CryptoCacheProperties;
import com.tripbler.backend.crypto.dto.CryptoHistoryPeriod;
import com.tripbler.backend.crypto.dto.CryptoHistoryResponse;
import com.tripbler.backend.crypto.dto.CryptoPriceResponse;
import com.tripbler.backend.crypto.domain.CryptoCoin;

/**
 * 서버 인스턴스별 메모리 캐시. 서비스 검증 후 정규화된 키만 받는다.
 * 현재 7개 코인/KRW와 5개 기간을 지원하므로 키 개수는 최대 42개다.
 * 심볼/API ID 별칭은 서비스에서 같은 CryptoCoin으로 변환되므로 캐시가 중복되지 않는다.
 * 여러 서버를 운영할 경우 Redis 등 공유 캐시로 전환해야 한다.
 */
@Component
public class CryptoResponseCache {
    private record PriceKey(CryptoCoin coin, String currency) {}
    private record HistoryKey(CryptoCoin coin, String currency, CryptoHistoryPeriod period) {}
    private record Entry<T>(T response, Instant expiresAt) {}
    private static final class Slot<T> {
        private Entry<T> entry;
    }

    private final ConcurrentHashMap<PriceKey, Slot<CryptoPriceResponse>> prices = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<HistoryKey, Slot<CryptoHistoryResponse>> histories = new ConcurrentHashMap<>();
    private final Duration priceTtl;
    private final Duration historyTtl;
    private final Clock clock;

    @Autowired
    public CryptoResponseCache(CryptoCacheProperties properties) {
        this(properties, Clock.systemUTC());
    }

    CryptoResponseCache(CryptoCacheProperties properties, Clock clock) {
        this.priceTtl = properties.getPriceTtl();
        this.historyTtl = properties.getHistoryTtl();
        this.clock = clock;
    }

    public CryptoPriceResponse getPrice(CryptoCoin coin, String currency, Supplier<CryptoPriceResponse> loader) {
        return get(prices, new PriceKey(coin, currency), priceTtl, loader);
    }

    public CryptoHistoryResponse getHistory(CryptoCoin coin, String currency, CryptoHistoryPeriod period,
                                           Supplier<CryptoHistoryResponse> loader) {
        return get(histories, new HistoryKey(coin, currency, period), historyTtl, loader);
    }

    private <K, T> T get(ConcurrentHashMap<K, Slot<T>> cache, K key, Duration ttl, Supplier<T> loader) {
        // 외부 통신을 map.compute 안에서 실행하지 않는다. 해시 버킷 충돌 시에도
        // 다른 코인/기간 조회를 막지 않도록 실제 로딩은 키별 잠금으로 분리한다.
        Slot<T> slot = cache.computeIfAbsent(key, ignored -> new Slot<>());
        synchronized (slot) {
            Entry<T> previous = slot.entry;
            if (previous != null && clock.instant().isBefore(previous.expiresAt())) {
                return previous.response();
            }
            // 예외 발생 시 새 항목은 저장되지 않는다. 만료된 항목도 성공 응답으로 반환하지 않는다.
            T response = Objects.requireNonNull(loader.get());
            slot.entry = new Entry<>(response, clock.instant().plus(ttl));
            return response;
        }
    }
}
