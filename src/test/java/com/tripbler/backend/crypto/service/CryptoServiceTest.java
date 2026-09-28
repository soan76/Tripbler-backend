package com.tripbler.backend.crypto.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.tripbler.backend.common.exception.BusinessException;
import com.tripbler.backend.common.exception.ErrorCode;
import com.tripbler.backend.crypto.client.CryptoMarketClient;
import com.tripbler.backend.crypto.dto.CryptoPriceResponse;

class CryptoServiceTest {
    private final CryptoMarketClient client = mock(CryptoMarketClient.class);
    private final CryptoService service = new CryptoService(client);

    @Test
    void normalizesInputAndDelegatesToInterface() {
        var expected = new CryptoPriceResponse("bitcoin", "KRW", new BigDecimal("100000000"), LocalDateTime.now());
        when(client.getCurrentPrice("bitcoin", "KRW")).thenReturn(expected);
        assertSame(expected, service.getCurrentPrice(" Bitcoin ", " krw "));
        verify(client).getCurrentPrice("bitcoin", "KRW");
    }

    @ParameterizedTest
    @CsvSource({"ethereum,KRW", "bitcoin,USD", "bitcoin, ", " ,KRW", "'',KRW", "'bitcoin,ethereum',KRW"})
    void rejectsUnsupportedOrEmptyInputBeforeCallingProvider(String coin, String currency) {
        var error = assertThrows(BusinessException.class, () -> service.getCurrentPrice(coin, currency));
        assertEquals(ErrorCode.INVALID_REQUEST, error.getErrorCode());
        verifyNoInteractions(client);
    }
}
