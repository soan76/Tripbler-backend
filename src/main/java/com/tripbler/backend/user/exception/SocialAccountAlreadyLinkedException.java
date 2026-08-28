package com.tripbler.backend.user.exception;

import com.tripbler.backend.common.exception.BusinessException;
import com.tripbler.backend.common.exception.ErrorCode;

// 사용자가 동일한 플랫폼 계정을 이미 연동한 경우 발생한다.
public class SocialAccountAlreadyLinkedException extends BusinessException {

    public SocialAccountAlreadyLinkedException() {
        super(ErrorCode.SOCIAL_ACCOUNT_ALREADY_LINKED);
    }
}