package com.tripbler.backend.common.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {

    INVALID_REQUEST(
        HttpStatus.BAD_REQUEST,
        "INVALID_REQUEST",
        "요청값이 올바르지 않습니다."
    ),

    INVALID_CURRENCY_CODE(
        HttpStatus.BAD_REQUEST,
        "INVALID_CURRENCY_CODE",
        "통화 코드가 올바르지 않습니다."
    ),

    TARGET_CURRENCY_REQUIRED(
        HttpStatus.BAD_REQUEST,
        "TARGET_CURRENCY_REQUIRED",
        "조회할 대상 통화를 하나 이상 입력해야 합니다."
    ),

    EXCHANGE_PROVIDER_UNAVAILABLE(
        HttpStatus.SERVICE_UNAVAILABLE,
        "EXCHANGE_PROVIDER_UNAVAILABLE",
        "현재 환율 제공 서비스를 이용할 수 없습니다."
    ),

    INTERNAL_SERVER_ERROR(
        HttpStatus.INTERNAL_SERVER_ERROR,
        "INTERNAL_SERVER_ERROR",
        "서버 내부 오류가 발생했습니다."
    );

    private final HttpStatus status;
    private final String code;
    private final String message;

    ErrorCode(
        HttpStatus status,
        String code,
        String message
    ) {
        this.status = status;
        this.code = code;
        this.message = message;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}