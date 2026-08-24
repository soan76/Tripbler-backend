package com.tripbler.backend.auth.exception;

import com.tripbler.backend.common.exception.BusinessException;
import com.tripbler.backend.common.exception.ErrorCode;

public class InvalidRefreshTokenException
    extends BusinessException {

    public InvalidRefreshTokenException() {
        super(ErrorCode.INVALID_REFRESH_TOKEN);
    }
}