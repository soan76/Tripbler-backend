package com.tripbler.backend.crypto.client;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Map;
import java.util.List;
import java.util.TreeMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.tripbler.backend.common.exception.BusinessException;
import com.tripbler.backend.common.exception.ErrorCode;
import com.tripbler.backend.crypto.config.CoinGeckoProperties;
import com.tripbler.backend.crypto.dto.CryptoPriceResponse;
import com.tripbler.backend.crypto.dto.CryptoHistoryPeriod;
import com.tripbler.backend.crypto.dto.CryptoHistoryPoint;
import com.tripbler.backend.crypto.dto.CryptoHistoryResponse;

@Component
public class CoinGeckoCryptoMarketClient implements CryptoMarketClient {
    private static final String BASE_URL = "https://api.coingecko.com/api/v3";
    private static final ParameterizedTypeReference<Map<String, Map<String, BigDecimal>>> PRICE_TYPE =
        new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<Map<String, List<List<BigDecimal>>>> HISTORY_TYPE =
        new ParameterizedTypeReference<>() {};

    private final RestClient restClient;
    private final CoinGeckoProperties properties;

    @Autowired
    public CoinGeckoCryptoMarketClient(RestClient.Builder builder, CoinGeckoProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(5));
        this.restClient = builder.baseUrl(BASE_URL).requestFactory(factory).build();
        this.properties = properties;
    }

    // 실제 네트워크 요청 없이 HTTP 요청/응답을 검증하기 위한 생성자.
    CoinGeckoCryptoMarketClient(RestClient restClient, CoinGeckoProperties properties) {
        this.restClient = restClient;
        this.properties = properties;
    }

    @Override
    public CryptoPriceResponse getCurrentPrice(String coin, String currency) {
        String apiKey = properties.getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw unavailable();
        }

        String quoteCurrency = currency.toLowerCase(Locale.ROOT);
        try {
            Map<String, Map<String, BigDecimal>> response = restClient.get()
                .uri(uriBuilder -> uriBuilder.path("/simple/price")
                    .queryParam("ids", coin)
                    .queryParam("vs_currencies", quoteCurrency)
                    .build())
                .header("x-cg-demo-api-key", apiKey.trim())
                .retrieve()
                .onStatus(status -> status.isError(), (request, providerResponse) -> {
                    throw unavailable();
                })
                .body(PRICE_TYPE);

            Map<String, BigDecimal> prices = response == null ? null : response.get(coin);
            BigDecimal price = prices == null ? null : prices.get(quoteCurrency);
            if (price == null || price.signum() <= 0) {
                throw unavailable();
            }

            return new CryptoPriceResponse(
                coin, currency.toUpperCase(Locale.ROOT), price, LocalDateTime.now()
            );
        } catch (RestClientException exception) {
            // 외부 응답 본문/인증 정보가 공통 오류 응답이나 예외 로그에 섞이지 않게 한다.
            throw unavailable();
        }
    }

    @Override
    public CryptoHistoryResponse getHistoricalPrices(String coin, String currency, CryptoHistoryPeriod period) {
        String apiKey = properties.getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw unavailable();
        }
        if (period == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "조회 기간은 필수입니다.");
        }

        try {
            Map<String, List<List<BigDecimal>>> response = restClient.get()
                .uri(uriBuilder -> uriBuilder.path("/coins/{id}/market_chart")
                    .queryParam("vs_currency", currency.toLowerCase(Locale.ROOT))
                    .queryParam("days", period.days())
                    // interval을 생략하여 Demo의 자동 간격을 사용한다.
                    .build(coin))
                .header("x-cg-demo-api-key", apiKey.trim())
                .retrieve()
                .onStatus(status -> status.isError(), (request, providerResponse) -> {
                    throw unavailable();
                })
                .body(HISTORY_TYPE);

            List<List<BigDecimal>> rawPrices = response == null ? null : response.get("prices");
            if (rawPrices == null || rawPrices.isEmpty()) {
                throw unavailable();
            }

            // 동일 시각은 마지막 값을 사용하고, 반환 순서는 시각 오름차순으로 고정한다.
            Map<Long, CryptoHistoryPoint> points = new TreeMap<>();
            for (List<BigDecimal> pair : rawPrices) {
                if (pair == null || pair.size() != 2 || pair.get(0) == null ||
                    pair.get(1) == null || pair.get(1).signum() <= 0) {
                    throw unavailable();
                }
                long timestamp = pair.get(0).longValueExact();
                if (timestamp < 0) {
                    throw unavailable();
                }
                points.put(timestamp, new CryptoHistoryPoint(timestamp, pair.get(1)));
            }

            return new CryptoHistoryResponse(
                coin, currency.toUpperCase(Locale.ROOT), period.code(), period.days(),
                List.copyOf(points.values()), LocalDateTime.now()
            );
        } catch (RestClientException | ArithmeticException exception) {
            // 제공자의 오류 본문이나 인증 정보를 공통 오류에 노출하지 않는다.
            throw unavailable();
        }
    }

    private BusinessException unavailable() {
        return new BusinessException(ErrorCode.CRYPTO_PROVIDER_UNAVAILABLE);
    }
}
