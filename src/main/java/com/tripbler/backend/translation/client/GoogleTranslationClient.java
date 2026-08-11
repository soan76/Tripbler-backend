package com.tripbler.backend.translation.client;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.tripbler.backend.common.exception.TranslationProviderException;
import com.tripbler.backend.translation.dto.GoogleTranslationResponse;

@Component
public class GoogleTranslationClient
    implements TranslationClient {

    private static final String GOOGLE_TRANSLATION_BASE_URL =
        "https://translation.googleapis.com";

    private static final Duration CONNECT_TIMEOUT =
        Duration.ofSeconds(3);

    private static final Duration READ_TIMEOUT =
        Duration.ofSeconds(5);

    private final RestClient restClient;
    private final String apiKey;
    
    @Autowired
    public GoogleTranslationClient(
        RestClient.Builder restClientBuilder,
        @Value("${google.translation.api-key}")
        String apiKey
    ) {
        SimpleClientHttpRequestFactory requestFactory =
            new SimpleClientHttpRequestFactory();

        requestFactory.setConnectTimeout(CONNECT_TIMEOUT);
        requestFactory.setReadTimeout(READ_TIMEOUT);

        this.restClient = restClientBuilder
            .baseUrl(GOOGLE_TRANSLATION_BASE_URL)
            .requestFactory(requestFactory)
            .build();

        this.apiKey = apiKey;
    }

    GoogleTranslationClient(
        RestClient restClient,
        String apiKey
    ) {
        this.restClient = restClient;
        this.apiKey = apiKey;
    }

    @Override
    public String translate(
        String text,
        String sourceLanguageCode,
        String targetLanguageCode
    ) {
        Map<String, Object> requestBody =
            new HashMap<>();

        requestBody.put("q", text);
        requestBody.put(
            "target",
            targetLanguageCode
        );
        requestBody.put(
            "format",
            "text"
        );

        if (!"auto".equalsIgnoreCase(
            sourceLanguageCode
        )) {
            requestBody.put(
                "source",
                sourceLanguageCode
            );
        }

        try {
            GoogleTranslationResponse response =
                restClient
                    .post()
                    .uri(uriBuilder -> uriBuilder
                        .path("/language/translate/v2")
                        .queryParam("key", apiKey)
                        .build()
                    )
                    .body(requestBody)
                    .retrieve()
                    .body(
                        GoogleTranslationResponse.class
                    );

            return extractTranslatedText(
                response
            );

        } catch (RestClientException exception) {
            throw new TranslationProviderException();
        }
    }

    private String extractTranslatedText(
        GoogleTranslationResponse response
    ) {
        if (
            response == null
                || response.data() == null
                || response.data()
                    .translations() == null
                || response.data()
                    .translations()
                    .isEmpty()
        ) {
            throw new TranslationProviderException();
        }

        String translatedText =
            response.data()
                .translations()
                .get(0)
                .translatedText();

        System.out.println(
            "Google 응답 translatedText = " + translatedText
        );

        if (
            translatedText == null
                || translatedText.isBlank()
        ) {
            throw new TranslationProviderException();
        }

        return decodeHtmlEntities(
            translatedText.trim()
        );
    }

    private String decodeHtmlEntities(
        String text
    ) {
        return text
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">");
    }
}
