package com.tripbler.backend.crypto.service;

import java.util.Locale;

import org.springframework.stereotype.Service;

import com.tripbler.backend.common.exception.BusinessException;
import com.tripbler.backend.common.exception.ErrorCode;
import com.tripbler.backend.crypto.client.CryptoMarketClient;
import com.tripbler.backend.crypto.dto.CryptoPriceResponse;
import com.tripbler.backend.crypto.dto.CryptoHistoryPeriod;
import com.tripbler.backend.crypto.dto.CryptoHistoryResponse;

@Service
public class CryptoService {
    private final CryptoMarketClient cryptoMarketClient;

    public CryptoService(CryptoMarketClient cryptoMarketClient) {
        this.cryptoMarketClient = cryptoMarketClient;
    }

    public CryptoPriceResponse getCurrentPrice(String coin, String currency) {
        String normalizedCoin = coin == null ? "" : coin.trim().toLowerCase(Locale.ROOT);
        String normalizedCurrency = currency == null ? "" : currency.trim().toUpperCase(Locale.ROOT);

        validatePair(normalizedCoin, normalizedCurrency);

        return cryptoMarketClient.getCurrentPrice(normalizedCoin, normalizedCurrency);
    }

    public CryptoHistoryResponse getHistoricalPrices(String coin, String currency, String period) {
        String normalizedCoin = coin == null ? "" : coin.trim().toLowerCase(Locale.ROOT);
        String normalizedCurrency = currency == null ? "" : currency.trim().toUpperCase(Locale.ROOT);
        validatePair(normalizedCoin, normalizedCurrency);

        String normalizedPeriod = period == null ? "" : period.trim().toUpperCase(Locale.ROOT);
        for (CryptoHistoryPeriod supported : CryptoHistoryPeriod.values()) {
            if (supported.code().equals(normalizedPeriod)) {
                return cryptoMarketClient.getHistoricalPrices(normalizedCoin, normalizedCurrency, supported);
            }
        }
        throw new BusinessException(
            ErrorCode.INVALID_REQUEST,
            "조회 기간은 7D, 1M, 3M, 6M, 1Y 중 하나여야 합니다. 최대 365일까지 지원합니다."
        );
    }

    private void validatePair(String normalizedCoin, String normalizedCurrency) {
        // 첫 구현의 지원 범위. 제공자를 바꾸어도 서비스의 계약은 유지한다.
        if (!"bitcoin".equals(normalizedCoin) || !"KRW".equals(normalizedCurrency)) {
            throw new BusinessException(
                ErrorCode.INVALID_REQUEST,
                "현재 bitcoin의 KRW 시세만 조회할 수 있습니다."
            );
        }
    }
}
