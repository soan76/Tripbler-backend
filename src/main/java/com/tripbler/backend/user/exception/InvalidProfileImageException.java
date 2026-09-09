package com.tripbler.backend.user.exception;

import com.tripbler.backend.common.exception.BusinessException;
import com.tripbler.backend.common.exception.ErrorCode;

public class InvalidProfileImageException
    extends BusinessException {

    public InvalidProfileImageException(
        String message
    ) {
        super(
            ErrorCode.INVALID_PROFILE_IMAGE,
            message
        );
    }
}