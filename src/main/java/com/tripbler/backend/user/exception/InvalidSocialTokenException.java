package com.tripbler.backend.user.exception;

import com.tripbler.backend.common.exception.BusinessException;
import com.tripbler.backend.common.exception.ErrorCode;

// 외부 플랫폼에서 전달된 인증 토큰이 유효하지 않은 경우 발생한다.
public class InvalidSocialTokenException extends BusinessException {

    public InvalidSocialTokenException() {
        super(ErrorCode.INVALID_SOCIAL_TOKEN);
    }

    public InvalidSocialTokenException(Throwable cause) {
        super(
            ErrorCode.INVALID_SOCIAL_TOKEN,
            ErrorCode.INVALID_SOCIAL_TOKEN.getMessage(),
            cause
        );
    }
}