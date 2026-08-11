package com.tripbler.backend.translation.service;

import org.springframework.stereotype.Service;

import com.tripbler.backend.translation.client.TranslationClient;
import com.tripbler.backend.translation.dto.TranslationRequest;
import com.tripbler.backend.translation.dto.TranslationResponse;

@Service
public class TranslationService {

    private final TranslationClient translationClient;

    public TranslationService(
        TranslationClient translationClient
    ) {
        this.translationClient =
            translationClient;
    }

    public TranslationResponse translate(
        TranslationRequest request
    ) {
        String text =
            request.text().trim();

        String sourceLanguageCode =
            request.sourceLanguageCode()
                .toLowerCase();

        String targetLanguageCode =
            request.targetLanguageCode()
                .toLowerCase();

        // 원본과 대상 언어가 같으면
        // 외부 API를 호출하지 않는다.
        if (
            sourceLanguageCode.equals(
                targetLanguageCode
            )
        ) {
            return new TranslationResponse(
                sourceLanguageCode,
                targetLanguageCode,
                text
            );
        }

        String translatedText =
            translationClient.translate(
                text,
                sourceLanguageCode,
                targetLanguageCode
            );

        return new TranslationResponse(
            sourceLanguageCode,
            targetLanguageCode,
            translatedText
        );
    }
}