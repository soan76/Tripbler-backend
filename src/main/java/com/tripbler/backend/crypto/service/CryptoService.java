package com.tripbler.backend.crypto.service;

import java.util.Locale;
import java.util.Arrays;
import java.util.List;
import com.tripbler.backend.crypto.dto.CryptoCoinResponse;

import org.springframework.stereotype.Service;

import com.tripbler.backend.common.exception.BusinessException;
import com.tripbler.backend.common.exception.ErrorCode;
import com.tripbler.backend.crypto.client.CryptoMarketClient;
import com.tripbler.backend.crypto.dto.CryptoPriceResponse;
import com.tripbler.backend.crypto.dto.CryptoHistoryPeriod;
import com.tripbler.backend.crypto.dto.CryptoHistoryResponse;
import com.tripbler.backend.crypto.domain.CryptoCoin;

@Service
public class CryptoService {
    private final CryptoMarketClient cryptoMarketClient;
    private final CryptoResponseCache cache;

    public CryptoService(CryptoMarketClient cryptoMarketClient, CryptoResponseCache cache) {
        this.cryptoMarketClient = cryptoMarketClient;
        this.cache = cache;
    }

    public List<CryptoCoinResponse> getSupportedCoins() {
        return Arrays.stream(CryptoCoin.values())
            .map(coin -> new CryptoCoinResponse(coin.symbol(), coin.displayName()))
            .toList();
    }

    public CryptoPriceResponse getCurrentPrice(String coin, String currency) {
        CryptoCoin normalizedCoin = CryptoCoin.resolve(coin);
        String normalizedCurrency = currency == null ? "" : currency.trim().toUpperCase(Locale.ROOT);

        validateCurrency(normalizedCurrency);

        return cache.getPrice(normalizedCoin, normalizedCurrency,
            () -> cryptoMarketClient.getCurrentPrice(normalizedCoin, normalizedCurrency));
    }

    public CryptoHistoryResponse getHistoricalPrices(String coin, String currency, String period) {
        CryptoCoin normalizedCoin = CryptoCoin.resolve(coin);
        String normalizedCurrency = currency == null ? "" : currency.trim().toUpperCase(Locale.ROOT);
        validateCurrency(normalizedCurrency);

        String normalizedPeriod = period == null ? "" : period.trim().toUpperCase(Locale.ROOT);
        for (CryptoHistoryPeriod supported : CryptoHistoryPeriod.values()) {
            if (supported.code().equals(normalizedPeriod)) {
                return cache.getHistory(normalizedCoin, normalizedCurrency, supported,
                    () -> cryptoMarketClient.getHistoricalPrices(normalizedCoin, normalizedCurrency, supported));
            }
        }
        throw new BusinessException(
            ErrorCode.INVALID_REQUEST,
            "조회 기간은 7D, 1M, 3M, 6M, 1Y 중 하나여야 합니다. 최대 365일까지 지원합니다."
        );
    }

    private void validateCurrency(String normalizedCurrency) {
        if (!"KRW".equals(normalizedCurrency)) {
            throw new BusinessException(
                ErrorCode.INVALID_REQUEST,
                "현재 KRW 기준 시세만 조회할 수 있습니다."
            );
        }
    }
}
