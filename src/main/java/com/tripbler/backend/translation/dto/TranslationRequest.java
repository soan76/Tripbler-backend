package com.tripbler.backend.translation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record TranslationRequest(

    @NotBlank(message = "번역할 텍스트는 비어 있을 수 없습니다.")
    @Size(
        max = 5000,
        message = "한 번에 번역할 수 있는 텍스트는 5000자 이하입니다."
    )
    String text,

    @NotBlank(message = "원본 언어 코드를 입력해 주세요.")
    @Pattern(
        regexp = "^(auto|[A-Za-z]{2,3})$",
        message = "원본 언어 코드가 올바르지 않습니다."
    )
    String sourceLanguageCode,

    @NotBlank(message = "번역 대상 언어 코드를 입력해 주세요.")
    @Pattern(
        regexp = "^[A-Za-z]{2,3}$",
        message = "번역 대상 언어 코드가 올바르지 않습니다."
    )
    String targetLanguageCode
) {
}