package com.tripbler.backend.common.exception;

// 요청 제한에 걸린 경우. GlobalExceptionHandler가 429와 Retry-After 헤더로 응답한다.
public class TooManyRequestsException extends BusinessException {

    private final long retryAfterSeconds;

    public TooManyRequestsException(long retryAfterSeconds) {
        super(ErrorCode.TOO_MANY_REQUESTS);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
