package com.tripbler.backend.exchange.controller;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.tripbler.backend.exchange.dto.ExchangeRateResponse;
import com.tripbler.backend.exchange.dto.HistoricalRateResponse;
import com.tripbler.backend.exchange.service.ExchangeService;

import jakarta.validation.constraints.Pattern;

@RestController
@RequestMapping("/api/v1/exchange")
public class ExchangeController {

    private final ExchangeService exchangeService;

    public ExchangeController(
        ExchangeService exchangeService
    ) {
        this.exchangeService = exchangeService;
    }

    @GetMapping("/rates")
    public ExchangeRateResponse getRates(
        @RequestParam(defaultValue = "KRW")
        @Pattern(
            regexp = "^[A-Za-z]{3}$",
            message = "기준 통화는 KRW, USD처럼 영문 3자리여야 합니다."
        )
        String base,

        @RequestParam(defaultValue = "USD,JPY,EUR")
        @Pattern(
            regexp = "^[A-Za-z]{3}(\\s*,\\s*[A-Za-z]{3})*$",
            message = "대상 통화는 USD,JPY,EUR 형식이어야 합니다."
        )
        String targets
    ) {
        return exchangeService.getRates(
            base,
            targets
        );
    }

    @GetMapping("/history")
    public HistoricalRateResponse getHistoricalRates(
        @RequestParam(defaultValue = "KRW")
        @Pattern(
            regexp = "^[A-Za-z]{3}$",
            message = "기준 통화는 KRW, USD처럼 영문 3자리여야 합니다."
        )
        String base,

        @RequestParam(defaultValue = "USD")
        @Pattern(
            regexp = "^[A-Za-z]{3}$",
            message = "대상 통화는 USD, JPY처럼 영문 3자리여야 합니다."
        )
        String target,

        @RequestParam
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate startDate,

        @RequestParam
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate endDate
    ) {
        return exchangeService.getHistoricalRates(
            base,
            target,
            startDate,
            endDate
        );
    }
}