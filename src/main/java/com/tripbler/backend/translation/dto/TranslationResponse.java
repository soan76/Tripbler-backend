package com.tripbler.backend.translation.dto;

public record TranslationResponse(
    String sourceLanguageCode,
    String targetLanguageCode,
    String translatedText
) {
}