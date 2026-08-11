package com.tripbler.backend.translation.dto;

import java.util.List;

public record GoogleTranslationResponse(
    Data data
) {

    public record Data(
        List<Translation> translations
    ) {
    }

    public record Translation(
        String translatedText,
        String detectedSourceLanguage
    ) {
    }
}