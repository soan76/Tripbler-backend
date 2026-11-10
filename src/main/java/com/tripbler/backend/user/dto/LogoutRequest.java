package com.tripbler.backend.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 로그아웃 요청. 이 기기(세션)의 Refresh Token 하나만 폐기한다.
 *
 * Access Token 없이도 보낼 수 있어 Access Token이 만료된 상태에서도 로그아웃이 서버에 반영된다.
 */
public record LogoutRequest(

    @NotBlank(message = "Refresh Token을 입력해 주세요.")
    @Size(max = 512, message = "Refresh Token 형식이 올바르지 않습니다.")
    String refreshToken

) {
}
