package com.tripbler.backend.crypto.controller;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

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
@Import({GlobalExceptionHandler.class, CryptoService.class})
class CryptoControllerTest {
    @Autowired private MockMvc mockMvc;
    @MockitoBean private CryptoMarketClient client;

    @Test
    void returnsTripblerDtoForRequestedPrice() throws Exception {
        when(client.getCurrentPrice("bitcoin", "KRW")).thenReturn(new CryptoPriceResponse(
            "bitcoin", "KRW", new BigDecimal("100000000.25"), LocalDateTime.of(2026, 9, 28, 12, 0)
        ));
        mockMvc.perform(get("/api/v1/crypto/price").param("coin", "bitcoin").param("currency", "KRW"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.coin").value("bitcoin"))
            .andExpect(jsonPath("$.currency").value("KRW"))
            .andExpect(jsonPath("$.price").value(100000000.25))
            .andExpect(jsonPath("$.fetchedAt").value("2026-09-28T12:00:00"))
            .andExpect(jsonPath("$.bitcoin").doesNotExist());
    }

    @Test
    void defaultsToBitcoinKrw() throws Exception {
        when(client.getCurrentPrice("bitcoin", "KRW")).thenReturn(new CryptoPriceResponse(
            "bitcoin", "KRW", BigDecimal.ONE, LocalDateTime.now()
        ));
        mockMvc.perform(get("/api/v1/crypto/price")).andExpect(status().isOk());
        verify(client).getCurrentPrice("bitcoin", "KRW");
    }

    @Test
    void invalidRequestUsesExistingErrorFormat() throws Exception {
        mockMvc.perform(get("/api/v1/crypto/price").param("coin", "ethereum"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
            .andExpect(jsonPath("$.path").value("/api/v1/crypto/price"));
        verifyNoInteractions(client);
    }

    @Test
    void unavailableProviderUsesExistingErrorFormat() throws Exception {
        when(client.getCurrentPrice("bitcoin", "KRW"))
            .thenThrow(new BusinessException(ErrorCode.CRYPTO_PROVIDER_UNAVAILABLE));
        mockMvc.perform(get("/api/v1/crypto/price"))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.status").value(503))
            .andExpect(jsonPath("$.code").value("CRYPTO_PROVIDER_UNAVAILABLE"))
            .andExpect(jsonPath("$.path").value("/api/v1/crypto/price"));
    }
}
