package com.tripbler.backend.common.exception;

public class TranslationProviderException extends BusinessException {

    public TranslationProviderException() {
        super(
            ErrorCode.TRANSLATION_PROVIDER_UNAVAILABLE
        );
    }
    
    public TranslationProviderException(
        Throwable cause
    ) {
        super(
            ErrorCode.TRANSLATION_PROVIDER_UNAVAILABLE,
            ErrorCode.TRANSLATION_PROVIDER_UNAVAILABLE
                .getMessage(),
            cause
        );
    }
}