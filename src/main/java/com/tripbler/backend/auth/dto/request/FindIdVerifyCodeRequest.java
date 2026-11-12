package com.tripbler.backend.auth.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record FindIdVerifyCodeRequest(

    @NotBlank(message = "이메일을 입력해 주세요.")
    @Email(message = "올바른 이메일 형식이어야 합니다.")
    // 제한기 키(Java 소문자)와 계정 조회(DB upper)가 같은 판정을 하도록 ASCII만 받는다.
    @Pattern(regexp = "^[\\x21-\\x7E]+$", message = "올바른 이메일 형식이어야 합니다.")
    @Size(max = 254, message = "이메일은 254자 이하여야 합니다.")
    String email,

    @NotBlank(message = "인증코드를 입력해 주세요.")
    @Pattern(
        regexp = "\\d{6}",
        message = "인증코드는 6자리 숫자여야 합니다."
    )
    String code

) {
}