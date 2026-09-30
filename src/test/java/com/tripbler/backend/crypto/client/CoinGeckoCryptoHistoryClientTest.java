package com.tripbler.backend.crypto.client;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

import java.math.BigDecimal;
import com.tripbler.backend.crypto.domain.CryptoCoin;
import java.net.SocketTimeoutException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.tripbler.backend.common.exception.BusinessException;
import com.tripbler.backend.common.exception.ErrorCode;
import com.tripbler.backend.crypto.config.CoinGeckoProperties;
import com.tripbler.backend.crypto.dto.CryptoHistoryPeriod;

class CoinGeckoCryptoHistoryClientTest {
    private static final String BASE_URL = "https://api.coingecko.com/api/v3";
    private MockRestServiceServer server;
    private CoinGeckoCryptoMarketClient client;
    private CoinGeckoProperties properties;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        properties = new CoinGeckoProperties();
        properties.setApiKey("test-demo-key");
        client = new CoinGeckoCryptoMarketClient(builder.build(), properties);
    }

    private String url(CryptoHistoryPeriod period) {
        return BASE_URL + "/coins/bitcoin/market_chart?vs_currency=krw&days=" + period.days();
    }

    @ParameterizedTest
    @EnumSource(CryptoHistoryPeriod.class)
    void requestsSupportedDaysAndPreservesTimestampsAndPrecision(CryptoHistoryPeriod period) {
        server.expect(requestTo(url(period)))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header("x-cg-demo-api-key", "test-demo-key"))
            .andRespond(withSuccess("""
                {
                  "prices": [
                    [1779028199661, 100000002.25],
                    [1779027899041, 100000001.123456789],
                    [1779028199661, 100000003.75]
                  ],
                  "market_caps": [[1779027899041, 2000000000]],
                  "total_volumes": [[1779027899041, 3000000000]]
                }
                """, MediaType.APPLICATION_JSON));

        var response = client.getHistoricalPrices(CryptoCoin.BTC, "KRW", period);
        assertEquals("BTC", response.symbol());
        assertEquals("KRW", response.currency());
        assertEquals(period.code(), response.period());
        assertEquals(period.days(), response.days());
        assertEquals(2, response.prices().size());
        assertEquals(1779027899041L, response.prices().get(0).timestamp());
        assertEquals(new BigDecimal("100000001.123456789"), response.prices().get(0).price());
        assertEquals(1779028199661L, response.prices().get(1).timestamp());
        assertEquals(new BigDecimal("100000003.75"), response.prices().get(1).price());
        assertNotNull(response.fetchedAt());
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "{}", "null", "{\"prices\":null}", "{\"prices\":[]}",
        "{\"prices\":[null]}", "{\"prices\":[[]]}", "{\"prices\":[[1]]}",
        "{\"prices\":[[1,2,3]]}", "{\"prices\":[[null,1]]}", "{\"prices\":[[1,null]]}",
        "{\"prices\":[[-1,1]]}", "{\"prices\":[[1.5,1]]}",
        "{\"prices\":[[999999999999999999999,1]]}",
        "{\"prices\":[[1,0]]}", "{\"prices\":[[1,-1]]}",
        "{\"prices\":[[1,\"invalid\"]]}", "invalid-json"
    })
    void malformedOrMissingHistoryReturnsProviderError(String body) {
        server.expect(requestTo(url(CryptoHistoryPeriod.SEVEN_DAYS)))
            .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
        assertUnavailable();
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(ints = {401, 403, 404, 429, 500, 503})
    void providerFailureUsesSafeCommonError(int status) {
        server.expect(requestTo(url(CryptoHistoryPeriod.SEVEN_DAYS)))
            .andRespond(withStatus(HttpStatusCode.valueOf(status))
                .body("private-provider-error").contentType(MediaType.TEXT_PLAIN));
        assertUnavailable();
        server.verify();
    }

    @Test
    void timeoutUsesSafeCommonError() {
        server.expect(requestTo(url(CryptoHistoryPeriod.SEVEN_DAYS)))
            .andRespond(withException(new SocketTimeoutException("timeout")));
        assertUnavailable();
        server.verify();
    }

    @Test
    void missingKeyDoesNotSendRequest() {
        properties.setApiKey("");
        assertUnavailable();
        server.verify();
    }

    private void assertUnavailable() {
        var error = assertThrows(BusinessException.class,
            () -> client.getHistoricalPrices(CryptoCoin.BTC, "KRW", CryptoHistoryPeriod.SEVEN_DAYS));
        assertEquals(ErrorCode.CRYPTO_PROVIDER_UNAVAILABLE, error.getErrorCode());
        assertEquals(ErrorCode.CRYPTO_PROVIDER_UNAVAILABLE.getMessage(), error.getMessage());
        assertNull(error.getCause());
    }
}
