package com.tripbler.backend.crypto.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.tripbler.backend.crypto.dto.CryptoPriceResponse;
import com.tripbler.backend.crypto.dto.CryptoHistoryResponse;
import com.tripbler.backend.crypto.dto.CryptoCoinResponse;
import java.util.List;
import com.tripbler.backend.crypto.service.CryptoService;

@RestController
@RequestMapping("/api/v1/crypto")
public class CryptoController {
    private final CryptoService cryptoService;

    public CryptoController(CryptoService cryptoService) {
        this.cryptoService = cryptoService;
    }

    @GetMapping("/coins")
    public List<CryptoCoinResponse> getCoins() {
        return cryptoService.getSupportedCoins();
    }

    @GetMapping("/history")
    public CryptoHistoryResponse getHistory(
        @RequestParam(defaultValue = "BTC") String coin,
        @RequestParam(defaultValue = "KRW") String currency,
        @RequestParam(defaultValue = "7D") String period,
        @RequestParam(defaultValue = "300") @jakarta.validation.constraints.Min(16)
        @jakarta.validation.constraints.Max(1000) int maxPoints
    ) {
        var response = cryptoService.getHistoricalPrices(coin, currency, period);
        return new CryptoHistoryResponse(response.symbol(), response.currency(), response.period(), response.days(),
            com.tripbler.backend.common.chart.HistoryDownsampler.reduce(response.prices(), maxPoints, point -> point.price().doubleValue()),
            response.fetchedAt(), response.stale());
    }

    @GetMapping("/price")
    public CryptoPriceResponse getPrice(
        @RequestParam(defaultValue = "BTC") String coin,
        @RequestParam(defaultValue = "KRW") String currency
    ) {
        return cryptoService.getCurrentPrice(coin, currency);
    }
}
