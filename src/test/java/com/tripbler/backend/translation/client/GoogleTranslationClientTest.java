package com.tripbler.backend.translation.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.tripbler.backend.common.exception.TranslationProviderException;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GoogleTranslationClientTest {

    private MockRestServiceServer mockServer;
    private GoogleTranslationClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder =
            RestClient.builder()
                .baseUrl(
                    "https://translation.googleapis.com"
                );

        mockServer =
            MockRestServiceServer.bindTo(builder)
                .build();

        RestClient restClient =
            builder.build();

        client =
            new GoogleTranslationClient(
                restClient,
                "test-api-key"
            );
    }

    @Test
    void 정상_번역응답을_반환한다() {
        mockServer.expect(
                requestTo(
                    "https://translation.googleapis.com"
                        + "/language/translate/v2"
                        + "?key=test-api-key"
                )
            )
            .andExpect(
                method(HttpMethod.POST)
            )
            .andRespond(
                withSuccess(
                    """
                    {
                      "data": {
                        "translations": [
                          {
                            "translatedText": "안녕하세요"
                          }
                        ]
                      }
                    }
                    """,
                    MediaType.APPLICATION_JSON
                )
            );

        String result =
            client.translate(
                "Hello",
                "en",
                "ko"
            );

        assertEquals(
            "안녕하세요",
            result
        );

        mockServer.verify();
    }

    @Test
    void Google_API가_500을_반환하면_TranslationProviderException을_던진다() {
        mockServer.expect(
                requestTo(
                    "https://translation.googleapis.com"
                        + "/language/translate/v2"
                        + "?key=test-api-key"
                )
            )
            .andExpect(
                method(HttpMethod.POST)
            )
            .andRespond(
                withServerError()
            );

        assertThrows(
            TranslationProviderException.class,
            () -> client.translate(
                "Hello",
                "en",
                "ko"
            )
        );

        mockServer.verify();
    }

    @Test
    void translations가_비어있으면_TranslationProviderException을_던진다() {
        mockServer.expect(
                requestTo(
                    "https://translation.googleapis.com"
                        + "/language/translate/v2"
                        + "?key=test-api-key"
                )
            )
            .andRespond(
                withSuccess(
                    """
                    {
                      "data": {
                        "translations": []
                      }
                    }
                    """,
                    MediaType.APPLICATION_JSON
                )
            );

        assertThrows(
            TranslationProviderException.class,
            () -> client.translate(
                "Hello",
                "en",
                "ko"
            )
        );

        mockServer.verify();
    }

    @Test
    void translatedText가_비어있으면_TranslationProviderException을_던진다() {
        mockServer.expect(
                requestTo(
                    "https://translation.googleapis.com"
                        + "/language/translate/v2"
                        + "?key=test-api-key"
                )
            )
            .andRespond(
                withSuccess(
                    """
                    {
                      "data": {
                        "translations": [
                          {
                            "translatedText": ""
                          }
                        ]
                      }
                    }
                    """,
                    MediaType.APPLICATION_JSON
                )
            );

        assertThrows(
            TranslationProviderException.class,
            () -> client.translate(
                "Hello",
                "en",
                "ko"
            )
        );

        mockServer.verify();
    }
}