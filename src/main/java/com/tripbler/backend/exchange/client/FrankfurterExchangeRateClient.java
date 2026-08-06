package com.tripbler.backend.exchange.client;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.tripbler.backend.common.exception.ExchangeProviderException;
import com.tripbler.backend.exchange.dto.ExchangeRateResponse;
import com.tripbler.backend.exchange.dto.FrankfurterHistoryResponse;
import com.tripbler.backend.exchange.dto.FrankfurterResponse;
import com.tripbler.backend.exchange.dto.HistoricalRatePoint;
import com.tripbler.backend.exchange.dto.HistoricalRateResponse;

@Component
public class FrankfurterExchangeRateClient
    implements ExchangeRateClient {

    private static final String FRANKFURTER_BASE_URL =
        "https://api.frankfurter.dev/v1";

    private static final Duration CONNECT_TIMEOUT =
    Duration.ofSeconds(3);

    private static final Duration READ_TIMEOUT =
    Duration.ofSeconds(5);

    private final RestClient restClient;

    @Autowired
    public FrankfurterExchangeRateClient(
        RestClient.Builder restClientBuilder
    ) {
        SimpleClientHttpRequestFactory requestFactory =
            new SimpleClientHttpRequestFactory();

        // 외부 환율 서버와 연결될 때까지 기다리는 최대 시간
        requestFactory.setConnectTimeout(CONNECT_TIMEOUT);

        // 연결 후 응답을 받을 때까지 기다리는 최대 시간
        requestFactory.setReadTimeout(READ_TIMEOUT);

        this.restClient = restClientBuilder
            .baseUrl(FRANKFURTER_BASE_URL)
            .requestFactory(requestFactory)
            .build();
    }

    // 단위 테스트에서 가짜 RestClient를 주입하기 위한 생성자
    FrankfurterExchangeRateClient(
        RestClient restClient
    ) {
        this.restClient = restClient;
    }

    @Override
    public ExchangeRateResponse getLatestRates(
        String baseCurrency,
        List<String> targetCurrencies
    ) {
        String symbols = String.join(
            ",",
            targetCurrencies
        );

        try {
            FrankfurterResponse response = restClient
                .get()
                .uri(uriBuilder -> uriBuilder
                    .path("/latest")
                    .queryParam("base", baseCurrency)
                    .queryParam("symbols", symbols)
                    .build())
                .retrieve()
                .onStatus(
                    status -> status.isError(),
                    (request, apiResponse) -> {
                        throw new ExchangeProviderException(
                            apiResponse
                                .getStatusCode()
                                .value()
                        );
                    }
                )
                .body(FrankfurterResponse.class);

            validateLatestResponse(response);

            return new ExchangeRateResponse(
                response.base(),
                response.rates(),
                response.date(),
                LocalDateTime.now()
            );

        } catch (ExchangeProviderException exception) {
            throw exception;

        } catch (RestClientException exception) {
            throw new ExchangeProviderException(exception);
        }
    }

    @Override
    public HistoricalRateResponse getHistoricalRates(
        String baseCurrency,
        String targetCurrency,
        LocalDate startDate,
        LocalDate endDate
    ) {
        String dateRange =
            startDate + ".." + endDate;

        try {
            FrankfurterHistoryResponse response = restClient
                .get()
                .uri(uriBuilder -> uriBuilder
                    .path("/{dateRange}")
                    .queryParam("base", baseCurrency)
                    .queryParam("symbols", targetCurrency)
                    .build(dateRange))
                .retrieve()
                .onStatus(
                    status -> status.isError(),
                    (request, apiResponse) -> {
                        throw new ExchangeProviderException(
                            apiResponse
                                .getStatusCode()
                                .value()
                        );
                    }
                )
                .body(FrankfurterHistoryResponse.class);

            validateHistoryResponse(response);

            List<HistoricalRatePoint> ratePoints =
                convertToRatePoints(
                    response.rates(),
                    targetCurrency
                );

            if (ratePoints.isEmpty()) {
                throw new ExchangeProviderException(
                    new IllegalStateException(
                        "기간별 환율 데이터가 없습니다."
                    )
                );
            }

            return new HistoricalRateResponse(
                response.base(),
                targetCurrency,
                response.startDate(),
                response.endDate(),
                ratePoints,
                LocalDateTime.now()
            );

        } catch (ExchangeProviderException exception) {
            throw exception;

        } catch (RestClientException exception) {
            throw new ExchangeProviderException(exception);
        }
    }

    private List<HistoricalRatePoint> convertToRatePoints(
        Map<LocalDate, Map<String, BigDecimal>> dailyRates,
        String targetCurrency
    ) {
        return dailyRates
            .entrySet()
            .stream()
            .map(entry -> {
                BigDecimal rate = entry
                    .getValue()
                    .get(targetCurrency);

                if (rate == null) {
                    return null;
                }

                return new HistoricalRatePoint(
                    entry.getKey(),
                    rate
                );
            })
            .filter(ratePoint -> ratePoint != null)
            .sorted(
                Comparator.comparing(
                    HistoricalRatePoint::date
                )
            )
            .toList();
    }

    private void validateLatestResponse(
        FrankfurterResponse response
    ) {
        if (
            response == null
                || response.base() == null
                || response.date() == null
                || response.rates() == null
                || response.rates().isEmpty()
        ) {
            throw new ExchangeProviderException(
                new IllegalStateException(
                    "Frankfurter 최신 환율 응답이 비어 있습니다."
                )
            );
        }
    }

    private void validateHistoryResponse(
        FrankfurterHistoryResponse response
    ) {
        if (
            response == null
                || response.base() == null
                || response.startDate() == null
                || response.endDate() == null
                || response.rates() == null
                || response.rates().isEmpty()
        ) {
            throw new ExchangeProviderException(
                new IllegalStateException(
                    "Frankfurter 기간별 환율 응답이 비어 있습니다."
                )
            );
        }
    }
}