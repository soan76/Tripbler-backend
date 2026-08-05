package com.tripbler.backend.common.exception;

import java.time.LocalDateTime;

public record ErrorResponse(
    LocalDateTime timestamp,
    int status,
    String code,
    String message,
    String path
) {

    public static ErrorResponse of(
        ErrorCode errorCode,
        String message,
        String path
    ) {
        return new ErrorResponse(
            LocalDateTime.now(),
            errorCode.getStatus().value(),
            errorCode.getCode(),
            message,
            path
        );
    }

    public static ErrorResponse of(
        ErrorCode errorCode,
        String path
    ) {
        return of(
            errorCode,
            errorCode.getMessage(),
            path
        );
    }
}