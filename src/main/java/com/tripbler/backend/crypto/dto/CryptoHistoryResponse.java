package com.tripbler.backend.crypto.dto;

import java.time.LocalDateTime;
import java.util.List;

/** prices는 시각 오름차순. fetchedAt은 Tripbler 조회 시각이다. */
public record CryptoHistoryResponse(
    String coin,
    String currency,
    String period,
    int days,
    List<CryptoHistoryPoint> prices,
    LocalDateTime fetchedAt
) {
    public CryptoHistoryResponse {
        prices = List.copyOf(prices);
    }
}
