package com.tripbler.backend.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UserPasswordChangeRequest(

    @NotBlank(message = "현재 비밀번호를 입력해 주세요.")
    String currentPassword,

    @NotBlank(message = "새 비밀번호를 입력해 주세요.")
    @Size(
        min = 8,
        max = 100,
        message = "새 비밀번호는 8자 이상 100자 이하여야 합니다."
    )
    String newPassword

) {
}