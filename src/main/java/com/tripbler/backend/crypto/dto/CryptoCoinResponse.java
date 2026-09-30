package com.tripbler.backend.crypto.dto;

/** Flutter 선택 목록용 공개 모델. 외부 API ID는 노출하지 않는다. */
public record CryptoCoinResponse(String symbol, String name) {
}
