package com.tripbler.backend.crypto.client;

import com.tripbler.backend.crypto.dto.CryptoPriceResponse;
import com.tripbler.backend.crypto.dto.CryptoHistoryPeriod;
import com.tripbler.backend.crypto.dto.CryptoHistoryResponse;

/** 외부 제공자와 무관한 암호화폐 시세 조회 계약. */
public interface CryptoMarketClient {
    CryptoPriceResponse getCurrentPrice(String coin, String currency);

    CryptoHistoryResponse getHistoricalPrices(String coin, String currency, CryptoHistoryPeriod period);
}
