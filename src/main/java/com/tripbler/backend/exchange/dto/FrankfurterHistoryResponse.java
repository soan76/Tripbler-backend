package com.tripbler.backend.exchange.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;

public record FrankfurterHistoryResponse(

    String base,

    @JsonProperty("start_date")
    LocalDate startDate,

    @JsonProperty("end_date")
    LocalDate endDate,

    Map<LocalDate, Map<String, BigDecimal>> rates

) {
}