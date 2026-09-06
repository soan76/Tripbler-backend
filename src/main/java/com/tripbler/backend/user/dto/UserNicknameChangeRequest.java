package com.tripbler.backend.user.dto;

import com.tripbler.backend.user.util.NicknameNormalizer;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UserNicknameChangeRequest(

    @NotBlank(message = "닉네임을 입력해 주세요.")
    @Size(
        min = 1,
        max = 20,
        message = "닉네임은 1자 이상 20자 이하여야 합니다."
    )
    String nickname

) {
    public UserNicknameChangeRequest {
        nickname = NicknameNormalizer.normalize(nickname);
    }
}