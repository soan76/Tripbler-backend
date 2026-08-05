package com.tripbler.backend.exchange.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record HistoricalRateResponse(
    String baseCurrency,
    String targetCurrency,
    LocalDate startDate,
    LocalDate endDate,
    List<HistoricalRatePoint> rates,
    LocalDateTime fetchedAt
) {
}