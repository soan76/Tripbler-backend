package com.tripbler.backend.crypto.controller;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.math.BigDecimal;
import com.tripbler.backend.crypto.domain.CryptoCoin;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.tripbler.backend.common.exception.BusinessException;
import com.tripbler.backend.common.exception.ErrorCode;
import com.tripbler.backend.common.exception.GlobalExceptionHandler;
import com.tripbler.backend.crypto.client.CryptoMarketClient;
import com.tripbler.backend.crypto.dto.*;
import com.tripbler.backend.crypto.service.CryptoService;

@WebMvcTest(CryptoController.class)
@Import({GlobalExceptionHandler.class, CryptoService.class, com.tripbler.backend.crypto.service.CryptoResponseCache.class, com.tripbler.backend.crypto.config.CryptoCacheProperties.class, com.tripbler.backend.common.config.SecurityConfig.class, com.tripbler.backend.common.security.CustomAuthenticationEntryPoint.class, com.tripbler.backend.common.security.CustomAccessDeniedHandler.class})
@org.springframework.test.annotation.DirtiesContext(classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class CryptoHistoryControllerTest {
    @Autowired private MockMvc mockMvc;
    @MockitoBean private CryptoMarketClient client;
    @MockitoBean private org.springframework.security.oauth2.jwt.JwtDecoder jwtDecoder;

    @Test
    void returnsTripblerHistoryDto() throws Exception {
        when(client.getHistoricalPrices(CryptoCoin.BTC, "KRW", CryptoHistoryPeriod.ONE_YEAR))
            .thenReturn(response(CryptoHistoryPeriod.ONE_YEAR));
        mockMvc.perform(get("/api/v1/crypto/history")
                .param("coin", "bitcoin").param("currency", "KRW").param("period", "1Y"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.symbol").value("BTC"))
            .andExpect(jsonPath("$.currency").value("KRW"))
            .andExpect(jsonPath("$.period").value("1Y"))
            .andExpect(jsonPath("$.days").value(365))
            .andExpect(jsonPath("$.prices[0].timestamp").value(1779027899041L))
            .andExpect(jsonPath("$.prices[0].price").value(100000001.25))
            .andExpect(jsonPath("$.fetchedAt").value("2026-09-28T12:00:00Z"))
            .andExpect(jsonPath("$.market_caps").doesNotExist())
            .andExpect(jsonPath("$.total_volumes").doesNotExist());
    }

    @Test
    void defaultsToSevenDays() throws Exception {
        when(client.getHistoricalPrices(CryptoCoin.BTC, "KRW", CryptoHistoryPeriod.SEVEN_DAYS))
            .thenReturn(response(CryptoHistoryPeriod.SEVEN_DAYS));
        mockMvc.perform(get("/api/v1/crypto/history"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.period").value("7D"));
        verify(client).getHistoricalPrices(CryptoCoin.BTC, "KRW", CryptoHistoryPeriod.SEVEN_DAYS);
    }

    @ParameterizedTest
    @ValueSource(strings = {"2Y", "5Y", "366", "max", "invalid"})
    void unsupportedPeriodUsesCommon400Response(String period) throws Exception {
        mockMvc.perform(get("/api/v1/crypto/history").param("period", period))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
            .andExpect(jsonPath("$.path").value("/api/v1/crypto/history"));
        verifyNoInteractions(client);
    }

    @Test
    void providerFailureUsesCommon503Response() throws Exception {
        when(client.getHistoricalPrices(CryptoCoin.BTC, "KRW", CryptoHistoryPeriod.SEVEN_DAYS))
            .thenThrow(new BusinessException(ErrorCode.CRYPTO_PROVIDER_UNAVAILABLE));
        mockMvc.perform(get("/api/v1/crypto/history"))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.code").value("CRYPTO_PROVIDER_UNAVAILABLE"))
            .andExpect(jsonPath("$.path").value("/api/v1/crypto/history"));
    }

    private CryptoHistoryResponse response(CryptoHistoryPeriod period) {
        return new CryptoHistoryResponse("BTC", "KRW", period.code(), period.days(),
            List.of(new CryptoHistoryPoint(1779027899041L, new BigDecimal("100000001.25"))),
            Instant.parse("2026-09-28T12:00:00Z"));
    }
}
