package com.tripbler.backend.exchange.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.tripbler.backend.common.exception.BusinessException;
import com.tripbler.backend.common.exception.ErrorCode;
import com.tripbler.backend.exchange.client.ExchangeRateClient;
import com.tripbler.backend.exchange.dto.ExchangeRateResponse;
import com.tripbler.backend.exchange.dto.HistoricalRateResponse;
import com.tripbler.backend.exchange.dto.HistoricalRatePoint;

@ExtendWith(MockitoExtension.class)
class ExchangeServiceTest {

    @Mock
    private ExchangeRateClient exchangeRateClient;

    @InjectMocks
    private ExchangeService exchangeService;

    @Test
    void 소문자_통화코드를_대문자로_변환한다() {
        ExchangeRateResponse expectedResponse =
            createResponse();

        when(
            exchangeRateClient.getLatestRates(
                "KRW",
                List.of("USD", "JPY")
            )
        ).thenReturn(expectedResponse);

        ExchangeRateResponse actualResponse =
            exchangeService.getRates(
                "krw",
                "usd,jpy"
            );

        assertEquals(
            expectedResponse,
            actualResponse
        );

        verify(exchangeRateClient)
            .getLatestRates(
                "KRW",
                List.of("USD", "JPY")
            );
    }

    @Test
    void 중복된_대상통화를_제거한다() {
        ExchangeRateResponse expectedResponse =
            createResponse();

        when(
            exchangeRateClient.getLatestRates(
                "KRW",
                List.of("USD", "JPY")
            )
        ).thenReturn(expectedResponse);

        exchangeService.getRates(
            "KRW",
            "USD,USD,JPY,JPY"
        );

        verify(exchangeRateClient)
            .getLatestRates(
                "KRW",
                List.of("USD", "JPY")
            );
    }

    @Test
    void 기준통화와_같은_대상통화를_제거한다() {
        ExchangeRateResponse expectedResponse =
            createResponse();

        when(
            exchangeRateClient.getLatestRates(
                "KRW",
                List.of("USD")
            )
        ).thenReturn(expectedResponse);

        exchangeService.getRates(
            "KRW",
            "KRW,USD"
        );

        verify(exchangeRateClient)
            .getLatestRates(
                "KRW",
                List.of("USD")
            );
    }

    @Test
    void 영문_3자리가_아닌_통화코드는_거부한다() {
        BusinessException exception =
            assertThrows(
                BusinessException.class,
                () -> exchangeService.getRates(
                    "KOREA",
                    "USD"
                )
            );

        assertEquals(
            ErrorCode.INVALID_CURRENCY_CODE,
            exception.getErrorCode()
        );
    }

    @Test
    void 대상통화가_비어있으면_거부한다() {
        BusinessException exception =
            assertThrows(
                BusinessException.class,
                () -> exchangeService.getRates(
                    "KRW",
                    " "
                )
            );

        assertEquals(
            ErrorCode.TARGET_CURRENCY_REQUIRED,
            exception.getErrorCode()
        );
    }

    @Test
    void 대상통화가_모두_기준통화이면_거부한다() {
        BusinessException exception =
            assertThrows(
                BusinessException.class,
                () -> exchangeService.getRates(
                    "KRW",
                    "KRW,KRW"
                )
            );

        assertEquals(
            ErrorCode.TARGET_CURRENCY_REQUIRED,
            exception.getErrorCode()
        );
    }

    @ParameterizedTest
    @CsvSource({
        "2023-03-01, 2024-03-01",
        "2022-03-01, 2024-03-01",
        "2019-03-01, 2024-03-01",
        "2019-02-28, 2024-02-29"
    })
    void supportsChartPeriodsIncludingLeapYears(LocalDate start, LocalDate end) {
        HistoricalRateResponse expected = new HistoricalRateResponse(
            "KRW", "USD", start, end,
            List.of(new HistoricalRatePoint(end, new BigDecimal("0.0007"))),
            LocalDateTime.of(2024, 3, 1, 12, 0)
        );
        when(exchangeRateClient.getHistoricalRates("KRW", "USD", start, end))
            .thenReturn(expected);

        assertEquals(expected,
            exchangeService.getHistoricalRates("KRW", "USD", start, end));
        verify(exchangeRateClient).getHistoricalRates("KRW", "USD", start, end);
    }

    @ParameterizedTest
    @CsvSource({
        "2019-02-28, 2024-03-01",
        "2019-02-27, 2024-02-29"
    })
    void rejectsPeriodsBeyondFiveYears(LocalDate start, LocalDate end) {
        BusinessException exception = assertThrows(BusinessException.class,
            () -> exchangeService.getHistoricalRates("KRW", "USD", start, end));

        assertEquals(ErrorCode.INVALID_REQUEST, exception.getErrorCode());
        assertEquals("기간별 환율은 최대 5년까지 조회할 수 있습니다.", exception.getMessage());
        verifyNoInteractions(exchangeRateClient);
    }

    private ExchangeRateResponse createResponse() {
        return new ExchangeRateResponse(
            "KRW",
            Map.of(
                "USD",
                new BigDecimal("0.0007"),
                "JPY",
                new BigDecimal("0.1103")
            ),
            LocalDate.of(2026, 8, 4),
            LocalDateTime.of(
                2026,
                8,
                5,
                15,
                30
            )
        );
    }
}
