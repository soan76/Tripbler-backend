package com.tripbler.backend.auth.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record FindIdVerifyCodeRequest(

    @NotBlank(message = "이메일을 입력해 주세요.")
    @Email(message = "올바른 이메일 형식이어야 합니다.")
    String email,

    @NotBlank(message = "인증코드를 입력해 주세요.")
    @Pattern(
        regexp = "\\d{6}",
        message = "인증코드는 6자리 숫자여야 합니다."
    )
    String code

) {
}