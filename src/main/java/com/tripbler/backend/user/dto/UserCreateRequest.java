package com.tripbler.backend.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UserCreateRequest(

    @NotBlank(message = "아이디는 필수입니다.")
    @Size(
        min = 4,
        max = 30,
        message = "아이디는 4자 이상 30자 이하여야 합니다."
    )
    String loginId,

    @NotBlank(message = "닉네임은 필수입니다.")
    @Size(
        min = 2,
        max = 20,
        message = "닉네임은 2자 이상 20자 이하여야 합니다."
    )
    String nickname,

    @NotBlank(message = "이메일은 필수입니다.")
    @Email(message = "올바른 이메일 형식이어야 합니다.")
    String email,

    @NotBlank(message = "비밀번호는 필수입니다.")
    @Size(
        min = 8,
        max = 72,
        message = "비밀번호는 8자 이상 72자 이하여야 합니다."
    )
    String password
) {
}