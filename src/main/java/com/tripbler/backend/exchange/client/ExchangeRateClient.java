package com.tripbler.backend.exchange.client;

import java.time.LocalDate;
import java.util.List;

import com.tripbler.backend.exchange.dto.ExchangeRateResponse;
import com.tripbler.backend.exchange.dto.HistoricalRateResponse;

public interface ExchangeRateClient {

    ExchangeRateResponse getLatestRates(
        String baseCurrency,
        List<String> targetCurrencies
    );

    HistoricalRateResponse getHistoricalRates(
        String baseCurrency,
        String targetCurrency,
        LocalDate startDate,
        LocalDate endDate
    );
}