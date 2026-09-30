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
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.tripbler.backend.common.exception.BusinessException;
import com.tripbler.backend.common.exception.ErrorCode;
import com.tripbler.backend.crypto.config.CoinGeckoProperties;

class CoinGeckoCryptoMarketClientTest {
    private static final String URL =
        "https://api.coingecko.com/api/v3/simple/price?ids=bitcoin&vs_currencies=krw";
    private MockRestServiceServer server;
    private CoinGeckoCryptoMarketClient client;
    private CoinGeckoProperties properties;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.coingecko.com/api/v3");
        server = MockRestServiceServer.bindTo(builder).build();
        properties = new CoinGeckoProperties();
        properties.setApiKey("test-demo-key");
        client = new CoinGeckoCryptoMarketClient(builder.build(), properties);
    }

    @Test
    void sendsDemoKeyInHeaderAndConvertsPriceWithoutLosingPrecision() {
        server.expect(requestTo(URL))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header("x-cg-demo-api-key", "test-demo-key"))
            .andRespond(withSuccess(
                "{\"bitcoin\":{\"krw\":123456789.123456789},\"ethereum\":{\"krw\":1}}",
                MediaType.APPLICATION_JSON
            ));

        var result = client.getCurrentPrice(CryptoCoin.BTC, "KRW");
        assertEquals("BTC", result.symbol());
        assertEquals("KRW", result.currency());
        assertEquals(new BigDecimal("123456789.123456789"), result.price());
        assertNotNull(result.fetchedAt());
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(ints = {401, 403, 429, 500, 503})
    void providerErrorsUseCommon503Code(int status) {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatusCode.valueOf(status))
            .body("provider-private-response").contentType(MediaType.TEXT_PLAIN));
        assertUnavailable();
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "{}", "null", "{\"bitcoin\":null}", "{\"bitcoin\":{}}",
        "{\"bitcoin\":{\"krw\":null}}", "{\"bitcoin\":{\"krw\":0}}",
        "{\"bitcoin\":{\"krw\":-1}}", "{\"bitcoin\":{\"krw\":\"bad\"}}", "invalid-json"
    })
    void invalidProviderDataDoesNotBecomeSuccessfulPrice(String body) {
        server.expect(requestTo(URL)).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
        assertUnavailable();
        server.verify();
    }

    @Test
    void connectionTimeoutUsesCommonError() {
        server.expect(requestTo(URL)).andRespond(withException(new SocketTimeoutException("timeout")));
        assertUnavailable();
        server.verify();
    }

    @Test
    void missingKeyDoesNotCallProvider() {
        properties.setApiKey(" ");
        assertUnavailable();
        server.verify();
    }

    private void assertUnavailable() {
        BusinessException error = assertThrows(BusinessException.class,
            () -> client.getCurrentPrice(CryptoCoin.BTC, "KRW"));
        assertEquals(ErrorCode.CRYPTO_PROVIDER_UNAVAILABLE, error.getErrorCode());
        assertEquals(ErrorCode.CRYPTO_PROVIDER_UNAVAILABLE.getMessage(), error.getMessage());
        assertNull(error.getCause());
    }
}
