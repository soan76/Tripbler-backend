package com.tripbler.backend.crypto.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import com.tripbler.backend.crypto.domain.CryptoCoin;
import java.util.List;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import com.tripbler.backend.common.exception.BusinessException;
import com.tripbler.backend.common.exception.ErrorCode;
import com.tripbler.backend.crypto.client.CryptoMarketClient;
import com.tripbler.backend.crypto.dto.CryptoHistoryPeriod;
import com.tripbler.backend.crypto.dto.CryptoHistoryResponse;

class CryptoHistoryServiceTest {
    private final CryptoMarketClient client = mock(CryptoMarketClient.class);
    private final CryptoService service = new CryptoService(client, new CryptoResponseCache(new com.tripbler.backend.crypto.config.CryptoCacheProperties()));

    @ParameterizedTest
    @CsvSource({"7D,SEVEN_DAYS,7", "1M,ONE_MONTH,30", "3M,THREE_MONTHS,90",
        "6M,SIX_MONTHS,180", "1Y,ONE_YEAR,365"})
    void mapsPeriodsWithinDemoLimit(String code, CryptoHistoryPeriod period, int days) {
        assertEquals(days, period.days());
        var expected = new CryptoHistoryResponse("BTC", "KRW", code, days, List.of(), Instant.now());
        when(client.getHistoricalPrices(CryptoCoin.BTC, "KRW", period)).thenReturn(expected);
        assertSame(expected, service.getHistoricalPrices(" Bitcoin ", " krw ", " " + code.toLowerCase() + " "));
        verify(client).getHistoricalPrices(CryptoCoin.BTC, "KRW", period);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "2Y", "5Y", "366", "365", "MAX", "1D"})
    void rejectsInvalidOrExcessivePeriodsBeforeCallingProvider(String period) {
        var error = assertThrows(BusinessException.class,
            () -> service.getHistoricalPrices("bitcoin", "KRW", period));
        assertEquals(ErrorCode.INVALID_REQUEST, error.getErrorCode());
        verifyNoInteractions(client);
    }

    @ParameterizedTest
    @CsvSource({"LTC,KRW", "bitcoin,USD", " ,KRW", "bitcoin, "})
    void rejectsUnsupportedPair(String coin, String currency) {
        assertThrows(BusinessException.class,
            () -> service.getHistoricalPrices(coin, currency, "7D"));
        verifyNoInteractions(client);
    }
}
