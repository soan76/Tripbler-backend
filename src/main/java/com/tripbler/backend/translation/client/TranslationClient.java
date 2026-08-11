package com.tripbler.backend.translation.client;

public interface TranslationClient {

    String translate(
        String text,
        String sourceLanguageCode,
        String targetLanguageCode
    );
}