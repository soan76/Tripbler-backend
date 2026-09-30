package com.tripbler.backend.crypto.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import com.tripbler.backend.crypto.domain.CryptoCoin;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.tripbler.backend.common.exception.BusinessException;
import com.tripbler.backend.common.exception.ErrorCode;
import com.tripbler.backend.crypto.client.CryptoMarketClient;
import com.tripbler.backend.crypto.dto.CryptoPriceResponse;

class CryptoServiceTest {
    private final CryptoMarketClient client = mock(CryptoMarketClient.class);
    private final CryptoService service = new CryptoService(client, new CryptoResponseCache(new com.tripbler.backend.crypto.config.CryptoCacheProperties()));

    @Test
    void normalizesInputAndDelegatesToInterface() {
        var expected = new CryptoPriceResponse("BTC", "KRW", new BigDecimal("100000000"), Instant.now());
        when(client.getCurrentPrice(CryptoCoin.BTC, "KRW")).thenReturn(expected);
        assertSame(expected, service.getCurrentPrice(" Bitcoin ", " krw "));
        verify(client).getCurrentPrice(CryptoCoin.BTC, "KRW");
    }

    @ParameterizedTest
    @CsvSource({"LTC,KRW", "bitcoin,USD", "bitcoin, ", " ,KRW", "'',KRW", "'bitcoin,ethereum',KRW"})
    void rejectsUnsupportedOrEmptyInputBeforeCallingProvider(String coin, String currency) {
        var error = assertThrows(BusinessException.class, () -> service.getCurrentPrice(coin, currency));
        assertEquals(ErrorCode.INVALID_REQUEST, error.getErrorCode());
        verifyNoInteractions(client);
    }
}
