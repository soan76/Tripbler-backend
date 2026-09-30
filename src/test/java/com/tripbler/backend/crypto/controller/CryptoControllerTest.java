package com.tripbler.backend.crypto.controller;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.math.BigDecimal;
import com.tripbler.backend.crypto.domain.CryptoCoin;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.tripbler.backend.common.exception.BusinessException;
import com.tripbler.backend.common.exception.ErrorCode;
import com.tripbler.backend.common.exception.GlobalExceptionHandler;
import com.tripbler.backend.crypto.client.CryptoMarketClient;
import com.tripbler.backend.crypto.dto.CryptoPriceResponse;
import com.tripbler.backend.crypto.service.CryptoService;

@WebMvcTest(CryptoController.class)
@Import({GlobalExceptionHandler.class, CryptoService.class, com.tripbler.backend.crypto.service.CryptoResponseCache.class, com.tripbler.backend.crypto.config.CryptoCacheProperties.class, com.tripbler.backend.common.config.SecurityConfig.class, com.tripbler.backend.common.security.CustomAuthenticationEntryPoint.class, com.tripbler.backend.common.security.CustomAccessDeniedHandler.class})
@org.springframework.test.annotation.DirtiesContext(classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class CryptoControllerTest {
    @Autowired private MockMvc mockMvc;

    @Test
    void listsPublicCoinsWithoutProviderIdsOrProviderCalls() throws Exception {
        mockMvc.perform(get("/api/v1/crypto/coins"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(7))
            .andExpect(jsonPath("$[0].symbol").value("BTC"))
            .andExpect(jsonPath("$[0].name").value("Bitcoin"))
            .andExpect(jsonPath("$[3].symbol").value("XRP"))
            .andExpect(jsonPath("$[3].name").value("XRP"))
            .andExpect(jsonPath("$[6].symbol").value("DASH"))
            .andExpect(jsonPath("$[*].coinGeckoId").isEmpty())
            .andExpect(jsonPath("$[*].coin").isEmpty());
        verifyNoInteractions(client);
    }
    @MockitoBean private CryptoMarketClient client;
    @MockitoBean private org.springframework.security.oauth2.jwt.JwtDecoder jwtDecoder;

    @Test
    void returnsTripblerDtoForRequestedPrice() throws Exception {
        when(client.getCurrentPrice(CryptoCoin.BTC, "KRW")).thenReturn(new CryptoPriceResponse(
            "BTC", "KRW", new BigDecimal("100000000.25"), Instant.parse("2026-09-28T12:00:00Z")
        ));
        mockMvc.perform(get("/api/v1/crypto/price").param("coin", "bitcoin").param("currency", "KRW"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.symbol").value("BTC"))
            .andExpect(jsonPath("$.currency").value("KRW"))
            .andExpect(jsonPath("$.price").value(100000000.25))
            .andExpect(jsonPath("$.fetchedAt").value("2026-09-28T12:00:00Z"))
            .andExpect(jsonPath("$.bitcoin").doesNotExist());
    }

    @Test
    void defaultsToBitcoinKrw() throws Exception {
        when(client.getCurrentPrice(CryptoCoin.BTC, "KRW")).thenReturn(new CryptoPriceResponse(
            "BTC", "KRW", BigDecimal.ONE, Instant.now()
        ));
        mockMvc.perform(get("/api/v1/crypto/price")).andExpect(status().isOk());
        verify(client).getCurrentPrice(CryptoCoin.BTC, "KRW");
    }

    @Test
    void invalidRequestUsesExistingErrorFormat() throws Exception {
        mockMvc.perform(get("/api/v1/crypto/price").param("coin", "LTC"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
            .andExpect(jsonPath("$.path").value("/api/v1/crypto/price"));
        verifyNoInteractions(client);
    }

    @Test
    void unavailableProviderUsesExistingErrorFormat() throws Exception {
        when(client.getCurrentPrice(CryptoCoin.BTC, "KRW"))
            .thenThrow(new BusinessException(ErrorCode.CRYPTO_PROVIDER_UNAVAILABLE));
        mockMvc.perform(get("/api/v1/crypto/price"))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.status").value(503))
            .andExpect(jsonPath("$.code").value("CRYPTO_PROVIDER_UNAVAILABLE"))
            .andExpect(jsonPath("$.path").value("/api/v1/crypto/price"));
    }
}
