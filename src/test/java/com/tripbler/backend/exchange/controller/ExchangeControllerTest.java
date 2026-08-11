package com.tripbler.backend.exchange.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.tripbler.backend.common.exception.BusinessException;
import com.tripbler.backend.common.exception.ErrorCode;
import com.tripbler.backend.common.exception.GlobalExceptionHandler;
import com.tripbler.backend.exchange.dto.ExchangeRateResponse;
import com.tripbler.backend.exchange.dto.HistoricalRatePoint;
import com.tripbler.backend.exchange.dto.HistoricalRateResponse;
import com.tripbler.backend.exchange.service.ExchangeService;
import com.tripbler.backend.common.exception.ExchangeProviderException;

@WebMvcTest(ExchangeController.class)
@Import(GlobalExceptionHandler.class)
class ExchangeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    // 실제 ExchangeService 대신 가짜 Mock 객체를 사용한다.
    @MockitoBean
    private ExchangeService exchangeService;

    @Test
    void shouldReturnLatestRatesWithStatus200() throws Exception {
        // 최신 환율 정상 요청 시 200과 환율 응답을 반환하는지 확인한다.
        ExchangeRateResponse response = new ExchangeRateResponse(
            "KRW",
            Map.of(
                "USD", new BigDecimal("0.0007"),
                "JPY", new BigDecimal("0.1103"),
                "EUR", new BigDecimal("0.00061")
            ),
            LocalDate.of(2026, 8, 4),
            LocalDateTime.of(2026, 8, 5, 17, 55, 14)
        );

        when(
            exchangeService.getRates(
                "KRW",
                "USD,JPY,EUR"
            )
        ).thenReturn(response);

        mockMvc.perform(
                get("/api/v1/exchange/rates")
                    .param("base", "KRW")
                    .param("targets", "USD,JPY,EUR")
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.baseCurrency").value("KRW"))
            .andExpect(jsonPath("$.rates.USD").value(0.0007))
            .andExpect(jsonPath("$.rates.JPY").value(0.1103))
            .andExpect(jsonPath("$.rates.EUR").value(0.00061))
            .andExpect(jsonPath("$.rateDate").value("2026-08-04"))
            .andExpect(
                jsonPath("$.fetchedAt")
                    .value("2026-08-05T17:55:14")
            );

        verify(exchangeService).getRates(
            "KRW",
            "USD,JPY,EUR"
        );
    }

    @Test
    void shouldUseDefaultParametersForLatestRates() throws Exception {
        // 쿼리 파라미터를 생략하면 Controller의 기본값이 사용되는지 확인한다.
        ExchangeRateResponse response = new ExchangeRateResponse(
            "KRW",
            Map.of(
                "USD", new BigDecimal("0.0007"),
                "JPY", new BigDecimal("0.1103"),
                "EUR", new BigDecimal("0.00061")
            ),
            LocalDate.of(2026, 8, 4),
            LocalDateTime.of(2026, 8, 5, 17, 55, 14)
        );

        when(
            exchangeService.getRates(
                "KRW",
                "USD,JPY,EUR"
            )
        ).thenReturn(response);

        mockMvc.perform(
                get("/api/v1/exchange/rates")
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.baseCurrency").value("KRW"));

        verify(exchangeService).getRates(
            "KRW",
            "USD,JPY,EUR"
        );
    }

    @Test
    void shouldReturn400WhenBaseCurrencyIsInvalid() throws Exception {
        // 기준 통화가 영문 3자리가 아니면 400을 반환하는지 확인한다.
        mockMvc.perform(
                get("/api/v1/exchange/rates")
                    .param("base", "KOREA")
                    .param("targets", "USD,JPY")
            )
            .andDo(print())
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
            .andExpect(
                jsonPath("$.message")
                    .value(
                        "기준 통화는 KRW, USD처럼 영문 3자리여야 합니다."
                    )
            )
            .andExpect(
                jsonPath("$.path")
                    .value("/api/v1/exchange/rates")
            );

        // Controller Validation에서 차단되므로 Service는 호출되지 않아야 한다.
        verifyNoInteractions(exchangeService);
    }

    @Test
    void shouldReturn400WhenTargetCurrencyFormatIsInvalid()
        throws Exception {

        // 대상 통화 목록이 쉼표 구분 형식이 아니면 400을 반환하는지 확인한다.
        mockMvc.perform(
                get("/api/v1/exchange/rates")
                    .param("base", "KRW")
                    .param("targets", "USD-JPY")
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
            .andExpect(
                jsonPath("$.message")
                    .value(
                        "대상 통화는 USD,JPY,EUR 형식이어야 합니다."
                    )
            )
            .andExpect(
                jsonPath("$.path")
                    .value("/api/v1/exchange/rates")
            );

        verifyNoInteractions(exchangeService);
    }

    @Test
    void shouldReturnHistoricalRatesWithStatus200() throws Exception {
        // 기간별 환율 정상 요청 시 200과 차트 데이터를 반환하는지 확인한다.
        HistoricalRateResponse response = new HistoricalRateResponse(
            "KRW",
            "USD",
            LocalDate.of(2026, 7, 1),
            LocalDate.of(2026, 7, 31),
            List.of(
                new HistoricalRatePoint(
                    LocalDate.of(2026, 7, 1),
                    new BigDecimal("0.00064")
                ),
                new HistoricalRatePoint(
                    LocalDate.of(2026, 7, 2),
                    new BigDecimal("0.00065")
                )
            ),
            LocalDateTime.of(2026, 8, 5, 22, 3, 47)
        );

        when(
            exchangeService.getHistoricalRates(
                "KRW",
                "USD",
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 7, 31)
            )
        ).thenReturn(response);

        mockMvc.perform(
                get("/api/v1/exchange/history")
                    .param("base", "KRW")
                    .param("target", "USD")
                    .param("startDate", "2026-07-01")
                    .param("endDate", "2026-07-31")
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.baseCurrency").value("KRW"))
            .andExpect(jsonPath("$.targetCurrency").value("USD"))
            .andExpect(jsonPath("$.startDate").value("2026-07-01"))
            .andExpect(jsonPath("$.endDate").value("2026-07-31"))
            .andExpect(jsonPath("$.rates.length()").value(2))
            .andExpect(
                jsonPath("$.rates[0].date")
                    .value("2026-07-01")
            )
            .andExpect(
                jsonPath("$.rates[0].rate")
                    .value(0.00064)
            )
            .andExpect(
                jsonPath("$.rates[1].date")
                    .value("2026-07-02")
            )
            .andExpect(
                jsonPath("$.rates[1].rate")
                    .value(0.00065)
            )
            .andExpect(
                jsonPath("$.fetchedAt")
                    .value("2026-08-05T22:03:47")
            );

        verify(exchangeService).getHistoricalRates(
            "KRW",
            "USD",
            LocalDate.of(2026, 7, 1),
            LocalDate.of(2026, 7, 31)
        );
    }

    @Test
    void shouldReturn400WhenHistoricalDateFormatIsInvalid()
        throws Exception {

        // 날짜가 YYYY-MM-DD 형식이 아니면 400을 반환하는지 확인한다.
        mockMvc.perform(
                get("/api/v1/exchange/history")
                    .param("base", "KRW")
                    .param("target", "USD")
                    .param("startDate", "2026/07/01")
                    .param("endDate", "2026-07-31")
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
            .andExpect(
                jsonPath("$.message")
                    .value(
                        "날짜는 YYYY-MM-DD 형식이어야 합니다."
                    )
            )
            .andExpect(
                jsonPath("$.path")
                    .value("/api/v1/exchange/history")
            );

        verifyNoInteractions(exchangeService);
    }

    @Test
    void shouldReturn400WhenBaseAndTargetCurrenciesAreSame()
        throws Exception {

        // 기준 통화와 대상 통화가 같으면 Service 예외가 400으로 변환되는지 확인한다.
        LocalDate startDate = LocalDate.of(2026, 7, 1);
        LocalDate endDate = LocalDate.of(2026, 7, 31);

        when(
            exchangeService.getHistoricalRates(
                "KRW",
                "KRW",
                startDate,
                endDate
            )
        ).thenThrow(
            new BusinessException(
                ErrorCode.INVALID_REQUEST,
                "기준 통화와 대상 통화는 서로 달라야 합니다."
            )
        );

        mockMvc.perform(
                get("/api/v1/exchange/history")
                    .param("base", "KRW")
                    .param("target", "KRW")
                    .param("startDate", "2026-07-01")
                    .param("endDate", "2026-07-31")
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
            .andExpect(
                jsonPath("$.message")
                    .value(
                        "기준 통화와 대상 통화는 서로 달라야 합니다."
                    )
            )
            .andExpect(
                jsonPath("$.path")
                    .value("/api/v1/exchange/history")
            );

        verify(exchangeService).getHistoricalRates(
            "KRW",
            "KRW",
            startDate,
            endDate
        );
    }

    @Test
    void shouldReturn400WhenStartDateIsAfterEndDate()
        throws Exception {

        // 시작일이 종료일보다 늦으면 Service 예외가 400으로 변환되는지 확인한다.
        LocalDate startDate = LocalDate.of(2026, 7, 31);
        LocalDate endDate = LocalDate.of(2026, 7, 1);

        when(
            exchangeService.getHistoricalRates(
                "KRW",
                "USD",
                startDate,
                endDate
            )
        ).thenThrow(
            new BusinessException(
                ErrorCode.INVALID_REQUEST,
                "시작일은 종료일보다 늦을 수 없습니다."
            )
        );

        mockMvc.perform(
                get("/api/v1/exchange/history")
                    .param("base", "KRW")
                    .param("target", "USD")
                    .param("startDate", "2026-07-31")
                    .param("endDate", "2026-07-01")
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
            .andExpect(
                jsonPath("$.message")
                    .value(
                        "시작일은 종료일보다 늦을 수 없습니다."
                    )
            )
            .andExpect(
                jsonPath("$.path")
                    .value("/api/v1/exchange/history")
            );

        verify(exchangeService).getHistoricalRates(
            "KRW",
            "USD",
            startDate,
            endDate
        );
    }

    @Test
    void shouldReturn503WhenLatestRateProviderIsUnavailable()
        throws Exception {

        // 최신 환율 외부 API 장애가 발생하면 503을 반환하는지 확인한다.
        when(
            exchangeService.getRates(
                "KRW",
                "USD,JPY,EUR"
            )
        ).thenThrow(
            new ExchangeProviderException(
                new RuntimeException(
                    "Frankfurter connection failed"
                )
            )
        );

        mockMvc.perform(
                get("/api/v1/exchange/rates")
                    .param("base", "KRW")
                    .param("targets", "USD,JPY,EUR")
            )
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.status").value(503))
            .andExpect(
                jsonPath("$.code")
                    .value("EXCHANGE_PROVIDER_UNAVAILABLE")
            )
            .andExpect(
                jsonPath("$.path")
                    .value("/api/v1/exchange/rates")
            );

        verify(exchangeService).getRates(
            "KRW",
            "USD,JPY,EUR"
        );
    }

    @Test
    void shouldReturn503WhenHistoricalRateProviderIsUnavailable()
        throws Exception {

        // 기간별 환율 외부 API 장애가 발생하면 503을 반환하는지 확인한다.
        LocalDate startDate = LocalDate.of(2026, 7, 1);
        LocalDate endDate = LocalDate.of(2026, 7, 31);

        when(
            exchangeService.getHistoricalRates(
                "KRW",
                "USD",
                startDate,
                endDate
            )
        ).thenThrow(
            new ExchangeProviderException(
                new RuntimeException(
                    "Frankfurter history request failed"
                )
            )
        );

        mockMvc.perform(
                get("/api/v1/exchange/history")
                    .param("base", "KRW")
                    .param("target", "USD")
                    .param("startDate", "2026-07-01")
                    .param("endDate", "2026-07-31")
            )
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.status").value(503))
            .andExpect(
                jsonPath("$.code")
                    .value("EXCHANGE_PROVIDER_UNAVAILABLE")
            )
            .andExpect(
                jsonPath("$.path")
                    .value("/api/v1/exchange/history")
            );

        verify(exchangeService).getHistoricalRates(
            "KRW",
            "USD",
            startDate,
            endDate
        );
    }

    @Test
    void shouldReturn500WhenUnexpectedErrorOccurs()
        throws Exception {

        // 예상하지 못한 서버 오류가 발생하면 500을 반환하는지 확인한다.
        when(
            exchangeService.getRates(
                "KRW",
                "USD"
            )
        ).thenThrow(
            new RuntimeException(
                "Unexpected server error"
            )
        );

        mockMvc.perform(
                get("/api/v1/exchange/rates")
                    .param("base", "KRW")
                    .param("targets", "USD")
            )
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.status").value(500))
            .andExpect(
                jsonPath("$.code")
                    .value("INTERNAL_SERVER_ERROR")
            )
            .andExpect(
                jsonPath("$.path")
                    .value("/api/v1/exchange/rates")
            );

        verify(exchangeService).getRates(
            "KRW",
            "USD"
        );
    }
}