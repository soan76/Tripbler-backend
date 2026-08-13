package com.tripbler.backend.common.exception;

public class PlacesProviderException
    extends RuntimeException {

    public PlacesProviderException(
        Throwable cause
    ) {
        super(
            "장소 제공 서비스에 연결할 수 없습니다.",
            cause
        );
    }
}