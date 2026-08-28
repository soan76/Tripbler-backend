package com.tripbler.backend.user.exception;

import com.tripbler.backend.common.exception.BusinessException;
import com.tripbler.backend.common.exception.ErrorCode;

// 플랫폼 계정이 다른 Tripbler 사용자에게 이미 연결된 경우 발생한다.
public class SocialAccountUsedByAnotherUserException extends BusinessException {

    public SocialAccountUsedByAnotherUserException() {
        super(ErrorCode.SOCIAL_ACCOUNT_USED_BY_ANOTHER_USER);
    }
}