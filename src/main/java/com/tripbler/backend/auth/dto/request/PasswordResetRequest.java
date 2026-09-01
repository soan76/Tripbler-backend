package com.tripbler.backend.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasswordResetRequest(

    @NotBlank(
        message = "비밀번호 재설정 인증 정보가 필요합니다."
    )
    String resetToken,

    @NotBlank(
        message = "새 비밀번호를 입력해 주세요."
    )
    @Size(
        min = 8,
        max = 100,
        message = "비밀번호는 8자 이상 100자 이하여야 합니다."
    )
    String newPassword

) {
}