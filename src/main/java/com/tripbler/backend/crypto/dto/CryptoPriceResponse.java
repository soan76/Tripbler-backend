package com.tripbler.backend.crypto.dto;

import java.math.BigDecimal;
import java.time.Instant;

/** fetchedAt은 UTC(Z) 조회 시각이며 캐시 반환 시에도 최초 조회 시각을 유지한다. */
public record CryptoPriceResponse(
    String symbol,
    String currency,
    BigDecimal price,
    Instant fetchedAt
) {
}
