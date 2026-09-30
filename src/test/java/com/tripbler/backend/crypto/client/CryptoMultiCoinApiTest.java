package com.tripbler.backend.crypto.client;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.client.RestClient;

import com.tripbler.backend.common.exception.GlobalExceptionHandler;
import com.tripbler.backend.crypto.config.CoinGeckoProperties;
import com.tripbler.backend.crypto.config.CryptoCacheProperties;
import com.tripbler.backend.crypto.controller.CryptoController;
import com.tripbler.backend.crypto.domain.CryptoCoin;
import com.tripbler.backend.crypto.service.CryptoResponseCache;
import com.tripbler.backend.crypto.service.CryptoService;

/** HTTP 컨트롤러부터 CoinGecko 요청까지 연결하고 외부 네트워크만 대체한다. */
class CryptoMultiCoinApiTest {
    private static final String BASE_URL = "https://api.coingecko.com/api/v3";
    private MockRestServiceServer provider;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        var builder = RestClient.builder().baseUrl(BASE_URL);
        provider = MockRestServiceServer.bindTo(builder).build();
        var properties = new CoinGeckoProperties();
        properties.setApiKey("test-demo-key");
        var client = new CoinGeckoCryptoMarketClient(builder.build(), properties);
        var service = new CryptoService(client, new CryptoResponseCache(new CryptoCacheProperties()));
        mvc = MockMvcBuilders.standaloneSetup(new CryptoController(service))
            .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @ParameterizedTest
    @CsvSource({"BTC,bitcoin", "ETH,ethereum", "SOL,solana", "XRP,ripple",
        "DOGE,dogecoin", "ADA,cardano", "DASH,dash"})
    void symbolsUseCorrectProviderIdAndShareCacheWithLegacyIds(String symbol, String id) throws Exception {
        assertEquals(id, CryptoCoin.resolve(symbol).coinGeckoId());
        assertEquals(symbol, CryptoCoin.resolve(id).symbol());
        provider.expect(requestTo(BASE_URL + "/simple/price?ids=" + id + "&vs_currencies=krw"))
            .andExpect(header("x-cg-demo-api-key", "test-demo-key"))
            .andRespond(withSuccess("{\"" + id + "\":{\"krw\":123.45}}", MediaType.APPLICATION_JSON));
        provider.expect(requestTo(BASE_URL + "/coins/" + id + "/market_chart?vs_currency=krw&days=7"))
            .andExpect(header("x-cg-demo-api-key", "test-demo-key"))
            .andRespond(withSuccess("{\"prices\":[[1779027899041,123.45]]}", MediaType.APPLICATION_JSON));

        var firstPrice = mvc.perform(get("/api/v1/crypto/price").param("coin", symbol))
            .andExpect(status().isOk()).andExpect(jsonPath("$.coin").doesNotExist())
            .andExpect(jsonPath("$.symbol").value(symbol))
            .andExpect(jsonPath("$.price").value(123.45)).andReturn().getResponse().getContentAsString();
        var firstHistory = mvc.perform(get("/api/v1/crypto/history").param("coin", symbol))
            .andExpect(status().isOk()).andExpect(jsonPath("$.coin").doesNotExist())
            .andExpect(jsonPath("$.symbol").value(symbol))
            .andExpect(jsonPath("$.prices[0].price").value(123.45)).andReturn().getResponse().getContentAsString();

        for (String alias : new String[]{id, " " + symbol.toLowerCase(java.util.Locale.ROOT) + " "}) {
            assertEquals(firstPrice, mvc.perform(get("/api/v1/crypto/price").param("coin", alias))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
            assertEquals(firstHistory, mvc.perform(get("/api/v1/crypto/history").param("coin", alias))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        }
        // 별칭으로 추가 외부 요청이 발생하면 MockRestServiceServer가 실패한다.
        provider.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"LTC", "USDT", "unknown", "BTC,ETH", "../bitcoin", "bitcoin-cash"})
    void unsupportedCoinsReturn400WithoutProviderRequest(String coin) throws Exception {
        for (String endpoint : new String[]{"price", "history"}) {
            mvc.perform(get("/api/v1/crypto/" + endpoint).param("coin", coin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        }
        provider.verify();
    }
}
