package com.tripbler.backend.user.exception;

import com.tripbler.backend.common.exception.BusinessException;
import com.tripbler.backend.common.exception.ErrorCode;

public class DuplicateNicknameException
    extends BusinessException {

    public DuplicateNicknameException() {
        super(ErrorCode.DUPLICATE_NICKNAME);
    }
}