package com.tripbler.backend.user.exception;

import com.tripbler.backend.common.exception.BusinessException;
import com.tripbler.backend.common.exception.ErrorCode;

public class DuplicateUserFieldException
    extends BusinessException {

    public DuplicateUserFieldException(
        Throwable cause
    ) {
        super(
            ErrorCode.DUPLICATE_USER_FIELD,
            ErrorCode.DUPLICATE_USER_FIELD
                .getMessage(),
            cause
        );
    }
}