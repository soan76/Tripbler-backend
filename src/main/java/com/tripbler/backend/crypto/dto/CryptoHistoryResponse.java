package com.tripbler.backend.crypto.dto;

import java.time.Instant;
import java.util.List;

/** prices는 시각 오름차순. fetchedAt은 캐시에도 보존되는 UTC(Z) 조회 시각이다. */
public record CryptoHistoryResponse(
    String symbol,
    String currency,
    String period,
    int days,
    List<CryptoHistoryPoint> prices,
    Instant fetchedAt
) {
    public CryptoHistoryResponse {
        prices = List.copyOf(prices);
    }

}
