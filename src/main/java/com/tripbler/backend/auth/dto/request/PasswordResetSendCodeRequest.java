package com.tripbler.backend.auth.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record PasswordResetSendCodeRequest(

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
    // 제한기 키(Java 소문자)와 계정 조회(DB upper)가 같은 판정을 하도록 ASCII만 받는다.
    @Pattern(
        regexp = "^[\\x21-\\x7E]+$",
        message = "올바른 이메일 형식을 입력해 주세요."
    )
    @Size(
        max = 254,
        message = "이메일은 254자 이하여야 합니다."
    )
    String email

) {
}