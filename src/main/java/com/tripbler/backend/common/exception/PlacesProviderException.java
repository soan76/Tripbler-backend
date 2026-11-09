package com.tripbler.backend.common.exception;

// 장소 제공 서비스(Google Places) 장애를 503으로 응답하기 위한 예외.
public class PlacesProviderException
    extends BusinessException {

    public PlacesProviderException(
        Throwable cause
    ) {
        super(
            ErrorCode.PLACES_PROVIDER_UNAVAILABLE,
            ErrorCode.PLACES_PROVIDER_UNAVAILABLE
                .getMessage(),
            cause
        );
    }
}
