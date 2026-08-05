package com.tripbler.backend.exchange.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record HistoricalRatePoint(
    LocalDate date,
    BigDecimal rate
) {
}