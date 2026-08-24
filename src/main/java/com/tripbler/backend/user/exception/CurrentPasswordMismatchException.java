package com.tripbler.backend.user.exception;

import com.tripbler.backend.common.exception.BusinessException;
import com.tripbler.backend.common.exception.ErrorCode;

public class CurrentPasswordMismatchException
    extends BusinessException {

    public CurrentPasswordMismatchException() {
        super(ErrorCode.CURRENT_PASSWORD_MISMATCH);
    }
}