package com.tripbler.backend.user.exception;

import com.tripbler.backend.common.exception.BusinessException;
import com.tripbler.backend.common.exception.ErrorCode;

public class ProfileImageStorageException
    extends BusinessException {

    public ProfileImageStorageException() {
        super(ErrorCode.PROFILE_IMAGE_STORAGE_FAILED);
    }

    public ProfileImageStorageException(
        String message
    ) {
        super(
            ErrorCode.PROFILE_IMAGE_STORAGE_FAILED,
            message
        );
    }

    public ProfileImageStorageException(
        String message,
        Throwable cause
    ) {
        super(
            ErrorCode.PROFILE_IMAGE_STORAGE_FAILED,
            message,
            cause
        );
    }
}