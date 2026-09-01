package com.tripbler.backend.auth.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record PasswordResetVerifyCodeRequest(

    @NotBlank(
        message = "아이디를 입력해 주세요."
    )
    @Size(
        min = 4,
        max = 30,
        message = "아이디는 4자 이상 30자 이하여야 합니다."
    )
    String loginId,

    @NotBlank(
        message = "이메일을 입력해 주세요."
    )
    @Email(
        message = "올바른 이메일 형식을 입력해 주세요."
    )
    String email,

    @NotBlank(
        message = "인증코드를 입력해 주세요."
    )
    @Pattern(
        regexp = "^\\d{6}$",
        message = "인증코드는 6자리 숫자여야 합니다."
    )
    String code

) {
}