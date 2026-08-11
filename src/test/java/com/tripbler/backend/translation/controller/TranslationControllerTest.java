package com.tripbler.backend.translation.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.tripbler.backend.common.exception.GlobalExceptionHandler;
import com.tripbler.backend.common.exception.TranslationProviderException;
import com.tripbler.backend.translation.dto.TranslationResponse;
import com.tripbler.backend.translation.service.TranslationService;

@WebMvcTest(TranslationController.class)
@Import(GlobalExceptionHandler.class)
class TranslationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TranslationService translationService;

    @Test
    void 정상_번역요청은_200을_반환한다()
        throws Exception {

        given(
            translationService.translate(any())
        ).willReturn(
            new TranslationResponse(
                "en",
                "ko",
                "안녕하세요"
            )
        );

        mockMvc.perform(
                post("/api/v1/translation")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "text": "Hello",
                          "sourceLanguageCode": "en",
                          "targetLanguageCode": "ko"
                        }
                        """)
            )
            .andExpect(status().isOk())
            .andExpect(
                content().contentTypeCompatibleWith(
                    MediaType.APPLICATION_JSON
                )
            )
            .andExpect(
                jsonPath("$.sourceLanguageCode")
                    .value("en")
            )
            .andExpect(
                jsonPath("$.targetLanguageCode")
                    .value("ko")
            )
            .andExpect(
                jsonPath("$.translatedText")
                    .value("안녕하세요")
            );
    }

    @Test
    void 번역할_텍스트가_비어있으면_400을_반환한다()
        throws Exception {

        mockMvc.perform(
                post("/api/v1/translation")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "text": "",
                          "sourceLanguageCode": "en",
                          "targetLanguageCode": "ko"
                        }
                        """)
            )
            .andExpect(status().isBadRequest())
            .andExpect(
                jsonPath("$.code")
                    .value("INVALID_REQUEST")
            );
    }

    @Test
    void 원본_언어코드가_잘못되면_400을_반환한다()
        throws Exception {

        mockMvc.perform(
                post("/api/v1/translation")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "text": "Hello",
                          "sourceLanguageCode": "english",
                          "targetLanguageCode": "ko"
                        }
                        """)
            )
            .andExpect(status().isBadRequest())
            .andExpect(
                jsonPath("$.code")
                    .value("INVALID_REQUEST")
            );
    }

    @Test
    void 대상_언어코드가_잘못되면_400을_반환한다()
        throws Exception {

        mockMvc.perform(
                post("/api/v1/translation")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "text": "Hello",
                          "sourceLanguageCode": "en",
                          "targetLanguageCode": "korean"
                        }
                        """)
            )
            .andExpect(status().isBadRequest())
            .andExpect(
                jsonPath("$.code")
                    .value("INVALID_REQUEST")
            );
    }

    @Test
    void 번역_제공서비스를_사용할수없으면_503을_반환한다()
        throws Exception {

        given(
            translationService.translate(any())
        ).willThrow(
            new TranslationProviderException()
        );

        mockMvc.perform(
                post("/api/v1/translation")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "text": "Hello",
                          "sourceLanguageCode": "en",
                          "targetLanguageCode": "ko"
                        }
                        """)
            )
            .andExpect(
                status().isServiceUnavailable()
            )
            .andExpect(
                jsonPath("$.code")
                    .value(
                        "TRANSLATION_PROVIDER_UNAVAILABLE"
                    )
            )
            .andExpect(
                jsonPath("$.message")
                    .value(
                        "현재 번역 서비스를 이용할 수 없습니다. 잠시 후 다시 시도해 주세요."
                    )
            )
            .andExpect(
                jsonPath("$.path")
                    .value("/api/v1/translation")
            );
    }

    @Test
    void 예상하지못한_오류가_발생하면_500을_반환한다()
        throws Exception {

        given(
            translationService.translate(any())
        ).willThrow(
            new RuntimeException(
                "예상하지 못한 오류"
            )
        );

        mockMvc.perform(
                post("/api/v1/translation")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "text": "Hello",
                          "sourceLanguageCode": "en",
                          "targetLanguageCode": "ko"
                        }
                        """)
            )
            .andExpect(
                status().isInternalServerError()
            )
            .andExpect(
                jsonPath("$.code")
                    .value("INTERNAL_SERVER_ERROR")
            )
            .andExpect(
                jsonPath("$.message")
                    .value(
                        "서버 내부 오류가 발생했습니다."
                    )
            );
    }
}