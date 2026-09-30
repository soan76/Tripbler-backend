package com.tripbler.backend.crypto.client;

import com.tripbler.backend.crypto.dto.CryptoPriceResponse;
import com.tripbler.backend.crypto.domain.CryptoCoin;
import com.tripbler.backend.crypto.dto.CryptoHistoryPeriod;
import com.tripbler.backend.crypto.dto.CryptoHistoryResponse;

/** 암호화폐 시세 조회 계약. coin은 제공자에 독립적인 지원 코인 타입이다. */
public interface CryptoMarketClient {
    CryptoPriceResponse getCurrentPrice(CryptoCoin coin, String currency);

    CryptoHistoryResponse getHistoricalPrices(CryptoCoin coin, String currency, CryptoHistoryPeriod period);
}
