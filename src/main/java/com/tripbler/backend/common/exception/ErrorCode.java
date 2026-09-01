package com.tripbler.backend.common.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {

    METHOD_NOT_ALLOWED(
        HttpStatus.METHOD_NOT_ALLOWED,
        "METHOD_NOT_ALLOWED",
        "지원하지 않는 HTTP 메서드입니다."
    ),

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

    DUPLICATE_LOGIN_ID(
        HttpStatus.CONFLICT,
        "DUPLICATE_LOGIN_ID",
        "이미 사용 중인 아이디입니다."
    ),

    USER_NOT_FOUND(
        HttpStatus.NOT_FOUND,
        "USER_NOT_FOUND",
        "사용자를 찾을 수 없습니다."
    ),

    INVALID_CREDENTIALS(
        HttpStatus.UNAUTHORIZED,
        "INVALID_CREDENTIALS",
        "아이디 또는 비밀번호가 올바르지 않습니다."
    ),

    CURRENT_PASSWORD_MISMATCH(
        HttpStatus.BAD_REQUEST,
        "CURRENT_PASSWORD_MISMATCH",
        "현재 비밀번호가 올바르지 않습니다."
    ),

    NEW_PASSWORD_SAME_AS_CURRENT(
        HttpStatus.BAD_REQUEST,
        "NEW_PASSWORD_SAME_AS_CURRENT",
        "새 비밀번호는 현재 비밀번호와 다르게 설정해 주세요."
    ),

    UNAUTHORIZED(
        HttpStatus.UNAUTHORIZED,
        "UNAUTHORIZED",
        "인증이 필요합니다."
    ),

    FORBIDDEN(
        HttpStatus.FORBIDDEN,
        "FORBIDDEN",
        "접근 권한이 없습니다."
    ),

    SOCIAL_ACCOUNT_ALREADY_LINKED(
        HttpStatus.CONFLICT,
        "SOCIAL_ACCOUNT_ALREADY_LINKED",
        "이미 해당 플랫폼 계정이 연동되어 있습니다."
    ),

    SOCIAL_ACCOUNT_USED_BY_ANOTHER_USER(
        HttpStatus.CONFLICT,
        "SOCIAL_ACCOUNT_USED_BY_ANOTHER_USER",
        "해당 플랫폼 계정은 다른 사용자에게 이미 연동되어 있습니다."
    ),

    INVALID_SOCIAL_TOKEN(
        HttpStatus.UNAUTHORIZED,
        "INVALID_SOCIAL_TOKEN",
        "외부 플랫폼 인증 정보가 올바르지 않습니다."
    ),

    INVALID_REFRESH_TOKEN(
        HttpStatus.UNAUTHORIZED,
        "INVALID_REFRESH_TOKEN",
        "Refresh Token이 유효하지 않습니다."
    ),

    EXPIRED_REFRESH_TOKEN(
        HttpStatus.UNAUTHORIZED,
        "EXPIRED_REFRESH_TOKEN",
        "Refresh Token이 만료되었습니다."
    ),

    EXCHANGE_PROVIDER_UNAVAILABLE(
        HttpStatus.SERVICE_UNAVAILABLE,
        "EXCHANGE_PROVIDER_UNAVAILABLE",
        "현재 환율 제공 서비스를 이용할 수 없습니다."
    ),

    TRANSLATION_PROVIDER_UNAVAILABLE(
        HttpStatus.SERVICE_UNAVAILABLE,
        "TRANSLATION_PROVIDER_UNAVAILABLE",
        "현재 번역 서비스를 이용할 수 없습니다. 잠시 후 다시 시도해 주세요."
    ),

    INVALID_FIND_ID_VERIFICATION_CODE(
        HttpStatus.BAD_REQUEST,
        "INVALID_FIND_ID_VERIFICATION_CODE",
        "인증코드가 맞지 않습니다."
    ),

    EXPIRED_FIND_ID_VERIFICATION_CODE(
        HttpStatus.BAD_REQUEST,
        "EXPIRED_FIND_ID_VERIFICATION_CODE",
        "인증코드가 만료되었습니다. 다시 발급해 주세요."
    ),

    INVALID_PASSWORD_RESET_VERIFICATION_CODE(
        HttpStatus.BAD_REQUEST,
        "INVALID_PASSWORD_RESET_VERIFICATION_CODE",
        "인증코드가 맞지 않습니다."
    ),

    EXPIRED_PASSWORD_RESET_VERIFICATION_CODE(
        HttpStatus.BAD_REQUEST,
        "EXPIRED_PASSWORD_RESET_VERIFICATION_CODE",
        "인증코드가 만료되었습니다. 다시 발급해 주세요."
    ),

    INVALID_PASSWORD_RESET_TOKEN(
        HttpStatus.UNAUTHORIZED,
        "INVALID_PASSWORD_RESET_TOKEN",
        "비밀번호 재설정 인증 정보가 유효하지 않습니다."
    ),

    EXPIRED_PASSWORD_RESET_TOKEN(
        HttpStatus.UNAUTHORIZED,
        "EXPIRED_PASSWORD_RESET_TOKEN",
        "비밀번호 재설정 인증이 만료되었습니다. 다시 인증해 주세요."
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