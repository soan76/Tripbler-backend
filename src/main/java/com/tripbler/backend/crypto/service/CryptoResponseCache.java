package com.tripbler.backend.crypto.service;

import java.time.Clock;
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
    private final com.tripbler.backend.common.cache.TimeoutFallbackCache<PriceKey, CryptoPriceResponse> prices;
    private final com.tripbler.backend.common.cache.TimeoutFallbackCache<HistoryKey, CryptoHistoryResponse> histories;

    @Autowired
    public CryptoResponseCache(CryptoCacheProperties properties) {
        this(properties, Clock.systemUTC());
    }

    CryptoResponseCache(CryptoCacheProperties properties, Clock clock) {
        prices = new com.tripbler.backend.common.cache.TimeoutFallbackCache<>(clock,
            properties.getPriceTtl(), properties.getPriceStaleWindow(), 64);
        histories = new com.tripbler.backend.common.cache.TimeoutFallbackCache<>(clock,
            properties.getHistoryTtl(), properties.getHistoryStaleWindow(), 64);
    }

    public CryptoPriceResponse getPrice(CryptoCoin coin, String currency, Supplier<CryptoPriceResponse> loader) {
        return prices.get(new PriceKey(coin, currency), loader, CryptoPriceResponse::asStale);
    }

    public CryptoHistoryResponse getHistory(CryptoCoin coin, String currency, CryptoHistoryPeriod period,
                                           Supplier<CryptoHistoryResponse> loader) {
        return histories.get(new HistoryKey(coin, currency, period), loader, CryptoHistoryResponse::asStale);
    }

}
