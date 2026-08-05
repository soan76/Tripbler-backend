package com.tripbler.backend.exchange.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

public record FrankfurterResponse(
    BigDecimal amount,
    String base,
    LocalDate date,
    Map<String, BigDecimal> rates
) {
}