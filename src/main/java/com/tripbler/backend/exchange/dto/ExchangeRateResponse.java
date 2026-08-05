package com.tripbler.backend.exchange.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

public record ExchangeRateResponse(
    String baseCurrency,
    Map<String, BigDecimal> rates,
    LocalDate rateDate,
    LocalDateTime fetchedAt
) {
}