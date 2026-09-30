package com.tripbler.backend.crypto.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.tripbler.backend.crypto.client.CryptoMarketClient;
import com.tripbler.backend.crypto.config.CryptoCacheProperties;
import com.tripbler.backend.crypto.domain.CryptoCoin;
import com.tripbler.backend.crypto.dto.*;

class CryptoMultiCoinCacheTest {
    @Test
    void sevenCoinsAndFivePeriodsHaveIndependentEntries() {
        var client = mock(CryptoMarketClient.class);
        var service = new CryptoService(client, new CryptoResponseCache(new CryptoCacheProperties()));
        for (CryptoCoin coin : CryptoCoin.values()) {
            CryptoCoin id = coin;
            when(client.getCurrentPrice(id, "KRW")).thenReturn(
                new CryptoPriceResponse(id.symbol(), "KRW", BigDecimal.valueOf(coin.ordinal() + 1), Instant.now()));
            for (CryptoHistoryPeriod period : CryptoHistoryPeriod.values()) {
                when(client.getHistoricalPrices(id, "KRW", period)).thenReturn(
                    new CryptoHistoryResponse(id.symbol(), "KRW", period.code(), period.days(), List.of(), Instant.now()));
            }
        }
        for (int round = 0; round < 2; round++) {
            for (CryptoCoin coin : CryptoCoin.values()) {
                String input = round == 0 ? coin.symbol() : coin.coinGeckoId();
                var price = service.getCurrentPrice(input, "KRW");
                assertEquals(coin.symbol(), price.symbol());
                assertEquals(BigDecimal.valueOf(coin.ordinal() + 1), price.price());
                for (CryptoHistoryPeriod period : CryptoHistoryPeriod.values()) {
                    var history = service.getHistoricalPrices(input, "KRW", period.code());
                    assertEquals(coin.symbol(), history.symbol());
                    assertEquals(period.code(), history.period());
                }
            }
        }
        for (CryptoCoin coin : CryptoCoin.values()) {
            verify(client, times(1)).getCurrentPrice(coin, "KRW");
            for (CryptoHistoryPeriod period : CryptoHistoryPeriod.values()) {
                verify(client, times(1)).getHistoricalPrices(coin, "KRW", period);
            }
        }
        verifyNoMoreInteractions(client);
    }
}
