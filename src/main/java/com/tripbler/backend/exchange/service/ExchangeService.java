package com.tripbler.backend.exchange.service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;

import com.tripbler.backend.common.exception.BusinessException;
import com.tripbler.backend.common.exception.ErrorCode;
import com.tripbler.backend.exchange.client.ExchangeRateClient;
import com.tripbler.backend.exchange.dto.ExchangeRateResponse;
import com.tripbler.backend.exchange.dto.HistoricalRateResponse;

@Service
public class ExchangeService {

    private static final long MAX_HISTORY_DAYS = 366;

    private final ExchangeRateClient exchangeRateClient;

    public ExchangeService(
        ExchangeRateClient exchangeRateClient
    ) {
        this.exchangeRateClient = exchangeRateClient;
    }

    public ExchangeRateResponse getRates(
        String baseCurrency,
        String targets
    ) {
        String normalizedBase =
            normalizeCurrencyCode(baseCurrency);

        List<String> targetCurrencies =
            parseTargetCurrencies(
                targets,
                normalizedBase
            );

        return exchangeRateClient.getLatestRates(
            normalizedBase,
            targetCurrencies
        );
    }

    public HistoricalRateResponse getHistoricalRates(
        String baseCurrency,
        String targetCurrency,
        LocalDate startDate,
        LocalDate endDate
    ) {
        String normalizedBase =
            normalizeCurrencyCode(baseCurrency);

        String normalizedTarget =
            normalizeCurrencyCode(targetCurrency);

        validateHistoryRequest(
            normalizedBase,
            normalizedTarget,
            startDate,
            endDate
        );

        return exchangeRateClient.getHistoricalRates(
            normalizedBase,
            normalizedTarget,
            startDate,
            endDate
        );
    }

    private void validateHistoryRequest(
        String baseCurrency,
        String targetCurrency,
        LocalDate startDate,
        LocalDate endDate
    ) {
        if (baseCurrency.equals(targetCurrency)) {
            throw new BusinessException(
                ErrorCode.INVALID_REQUEST,
                "기준 통화와 대상 통화는 서로 달라야 합니다."
            );
        }

        if (startDate == null || endDate == null) {
            throw new BusinessException(
                ErrorCode.INVALID_REQUEST,
                "시작일과 종료일은 필수입니다."
            );
        }

        if (startDate.isAfter(endDate)) {
            throw new BusinessException(
                ErrorCode.INVALID_REQUEST,
                "시작일은 종료일보다 늦을 수 없습니다."
            );
        }

        if (endDate.isAfter(LocalDate.now())) {
            throw new BusinessException(
                ErrorCode.INVALID_REQUEST,
                "종료일은 오늘보다 늦을 수 없습니다."
            );
        }

        long requestedDays =
            ChronoUnit.DAYS.between(
                startDate,
                endDate
            ) + 1;

        if (requestedDays > MAX_HISTORY_DAYS) {
            throw new BusinessException(
                ErrorCode.INVALID_REQUEST,
                "기간별 환율은 최대 366일까지 조회할 수 있습니다."
            );
        }
    }

    private List<String> parseTargetCurrencies(
        String targets,
        String baseCurrency
    ) {
        if (targets == null || targets.isBlank()) {
            throw new BusinessException(
                ErrorCode.TARGET_CURRENCY_REQUIRED
            );
        }

        List<String> currencies = Arrays
            .stream(targets.split(","))
            .map(String::trim)
            .filter(currency -> !currency.isBlank())
            .map(this::normalizeCurrencyCode)
            .filter(currency ->
                !currency.equals(baseCurrency)
            )
            .distinct()
            .toList();

        if (currencies.isEmpty()) {
            throw new BusinessException(
                ErrorCode.TARGET_CURRENCY_REQUIRED
            );
        }

        return currencies;
    }

    private String normalizeCurrencyCode(
        String currencyCode
    ) {
        if (currencyCode == null) {
            throw new BusinessException(
                ErrorCode.INVALID_CURRENCY_CODE
            );
        }

        String normalized = currencyCode
            .trim()
            .toUpperCase(Locale.ROOT);

        if (!normalized.matches("^[A-Z]{3}$")) {
            throw new BusinessException(
                ErrorCode.INVALID_CURRENCY_CODE
            );
        }

        return normalized;
    }
}