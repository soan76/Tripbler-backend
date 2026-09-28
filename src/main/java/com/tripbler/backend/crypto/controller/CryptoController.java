package com.tripbler.backend.crypto.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.tripbler.backend.crypto.dto.CryptoPriceResponse;
import com.tripbler.backend.crypto.dto.CryptoHistoryResponse;
import com.tripbler.backend.crypto.service.CryptoService;

@RestController
@RequestMapping("/api/v1/crypto")
public class CryptoController {
    private final CryptoService cryptoService;

    public CryptoController(CryptoService cryptoService) {
        this.cryptoService = cryptoService;
    }

    @GetMapping("/history")
    public CryptoHistoryResponse getHistory(
        @RequestParam(defaultValue = "bitcoin") String coin,
        @RequestParam(defaultValue = "KRW") String currency,
        @RequestParam(defaultValue = "7D") String period
    ) {
        return cryptoService.getHistoricalPrices(coin, currency, period);
    }

    @GetMapping("/price")
    public CryptoPriceResponse getPrice(
        @RequestParam(defaultValue = "bitcoin") String coin,
        @RequestParam(defaultValue = "KRW") String currency
    ) {
        return cryptoService.getCurrentPrice(coin, currency);
    }
}
