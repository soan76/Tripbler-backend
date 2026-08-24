package com.tripbler.backend.auth.exception;

import com.tripbler.backend.common.exception.BusinessException;
import com.tripbler.backend.common.exception.ErrorCode;

public class ExpiredRefreshTokenException
    extends BusinessException {

    public ExpiredRefreshTokenException() {
        super(ErrorCode.EXPIRED_REFRESH_TOKEN);
    }
}