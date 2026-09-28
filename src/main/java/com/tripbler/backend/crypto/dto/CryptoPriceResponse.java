package com.tripbler.backend.crypto.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** fetchedAt은 제공자의 가격 갱신 시각이 아닌 Tripbler의 조회 시각이다. */
public record CryptoPriceResponse(
    String coin,
    String currency,
    BigDecimal price,
    LocalDateTime fetchedAt
) {
}
