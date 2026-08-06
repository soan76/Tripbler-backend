package com.tripbler.backend.exchange.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.tripbler.backend.common.exception.ExchangeProviderException;
import com.tripbler.backend.exchange.dto.ExchangeRateResponse;
import com.tripbler.backend.exchange.dto.HistoricalRateResponse;

class FrankfurterExchangeRateClientTest {

    private static final String BASE_URL =
        "https://api.frankfurter.dev/v1";

    private MockRestServiceServer mockServer;

    private FrankfurterExchangeRateClient exchangeRateClient;

    @BeforeEach
    void setUp() {
        RestClient.Builder restClientBuilder =
            RestClient.builder()
                .baseUrl(BASE_URL);

        // RestClient가 실제 Frankfurter가 아닌 가짜 서버를 호출하도록 연결한다.
        mockServer = MockRestServiceServer
            .bindTo(restClientBuilder)
            .build();

        exchangeRateClient =
            new FrankfurterExchangeRateClient(
                restClientBuilder.build()
            );
    }

    @Test
    void shouldParseLatestRatesResponse() {
        // 최신 환율 정상 응답을 DTO로 변환하는지 확인한다.
        String responseBody = """
            {
              "amount": 1.0,
              "base": "KRW",
              "date": "2026-08-04",
              "rates": {
                "USD": 0.0007,
                "JPY": 0.1103,
                "EUR": 0.00061
              }
            }
            """;

        mockServer
            .expect(
                requestTo(
                    BASE_URL
                        + "/latest"
                        + "?base=KRW"
                        + "&symbols=USD,JPY,EUR"
                )
            )
            .andExpect(method(HttpMethod.GET))
            .andRespond(
                withSuccess(
                    responseBody,
                    MediaType.APPLICATION_JSON
                )
            );

        ExchangeRateResponse response =
            exchangeRateClient.getLatestRates(
                "KRW",
                List.of("USD", "JPY", "EUR")
            );

        assertEquals(
            "KRW",
            response.baseCurrency()
        );

        assertEquals(
            LocalDate.of(2026, 8, 4),
            response.rateDate()
        );

        assertEquals(
            new BigDecimal("0.0007"),
            response.rates().get("USD")
        );

        assertEquals(
            new BigDecimal("0.1103"),
            response.rates().get("JPY")
        );

        assertEquals(
            new BigDecimal("0.00061"),
            response.rates().get("EUR")
        );

        mockServer.verify();
    }

    @Test
    void shouldParseAndSortHistoricalRatesResponse() {
        // 날짜 순서가 섞인 기간별 응답을 날짜 오름차순으로 변환하는지 확인한다.
        String responseBody = """
            {
              "amount": 1.0,
              "base": "KRW",
              "start_date": "2026-07-01",
              "end_date": "2026-07-03",
              "rates": {
                "2026-07-03": {
                  "USD": 0.00066
                },
                "2026-07-01": {
                  "USD": 0.00064
                },
                "2026-07-02": {
                  "USD": 0.00065
                }
              }
            }
            """;

        mockServer
            .expect(
                requestTo(
                    BASE_URL
                        + "/2026-07-01..2026-07-03"
                        + "?base=KRW"
                        + "&symbols=USD"
                )
            )
            .andExpect(method(HttpMethod.GET))
            .andRespond(
                withSuccess(
                    responseBody,
                    MediaType.APPLICATION_JSON
                )
            );

        HistoricalRateResponse response =
            exchangeRateClient.getHistoricalRates(
                "KRW",
                "USD",
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 7, 3)
            );

        assertEquals(
            "KRW",
            response.baseCurrency()
        );

        assertEquals(
            "USD",
            response.targetCurrency()
        );

        assertEquals(
            3,
            response.rates().size()
        );

        assertEquals(
            LocalDate.of(2026, 7, 1),
            response.rates().get(0).date()
        );

        assertEquals(
            new BigDecimal("0.00064"),
            response.rates().get(0).rate()
        );

        assertEquals(
            LocalDate.of(2026, 7, 2),
            response.rates().get(1).date()
        );

        assertEquals(
            LocalDate.of(2026, 7, 3),
            response.rates().get(2).date()
        );

        mockServer.verify();
    }

    @Test
    void shouldThrowExchangeProviderExceptionWhenServerReturns500() {
        // 외부 환율 API가 500을 반환하면 공통 제공자 예외로 변환하는지 확인한다.
        mockServer
            .expect(
                requestTo(
                    BASE_URL
                        + "/latest"
                        + "?base=KRW"
                        + "&symbols=USD"
                )
            )
            .andExpect(method(HttpMethod.GET))
            .andRespond(withServerError());

        assertThrows(
            ExchangeProviderException.class,
            () -> exchangeRateClient.getLatestRates(
                "KRW",
                List.of("USD")
            )
        );

        mockServer.verify();
    }

    @Test
    void shouldThrowExchangeProviderExceptionWhenLatestResponseIsEmpty() {
        // 외부 API 응답에 필수 데이터가 없으면 예외가 발생하는지 확인한다.
        String responseBody = """
            {}
            """;

        mockServer
            .expect(
                requestTo(
                    BASE_URL
                        + "/latest"
                        + "?base=KRW"
                        + "&symbols=USD"
                )
            )
            .andExpect(method(HttpMethod.GET))
            .andRespond(
                withSuccess(
                    responseBody,
                    MediaType.APPLICATION_JSON
                )
            );

        assertThrows(
            ExchangeProviderException.class,
            () -> exchangeRateClient.getLatestRates(
                "KRW",
                List.of("USD")
            )
        );

        mockServer.verify();
    }

    @Test
    void shouldThrowExchangeProviderExceptionWhenHistoryResponseIsEmpty() {
        // 기간별 응답의 rates가 비어 있으면 예외가 발생하는지 확인한다.
        String responseBody = """
            {
              "base": "KRW",
              "start_date": "2026-07-01",
              "end_date": "2026-07-03",
              "rates": {}
            }
            """;

        mockServer
            .expect(
                requestTo(
                    BASE_URL
                        + "/2026-07-01..2026-07-03"
                        + "?base=KRW"
                        + "&symbols=USD"
                )
            )
            .andExpect(method(HttpMethod.GET))
            .andRespond(
                withSuccess(
                    responseBody,
                    MediaType.APPLICATION_JSON
                )
            );

        assertThrows(
            ExchangeProviderException.class,
            () -> exchangeRateClient.getHistoricalRates(
                "KRW",
                "USD",
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 7, 3)
            )
        );

        mockServer.verify();
    }

    @Test
    void shouldThrowExchangeProviderExceptionWhenNetworkFails() {
        // 네트워크 입출력 오류가 발생하면 공통 제공자 예외로 변환하는지 확인한다.
        mockServer
            .expect(
                requestTo(
                    BASE_URL
                        + "/latest"
                        + "?base=KRW"
                        + "&symbols=USD"
                )
            )
            .andExpect(method(HttpMethod.GET))
            .andRespond(
                withException(
                    new IOException(
                        "Connection timed out"
                    )
                )
            );

        assertThrows(
            ExchangeProviderException.class,
            () -> exchangeRateClient.getLatestRates(
                "KRW",
                List.of("USD")
            )
        );

        mockServer.verify();
    }
}