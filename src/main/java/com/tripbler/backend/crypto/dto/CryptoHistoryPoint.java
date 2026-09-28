package com.tripbler.backend.crypto.dto;

import java.math.BigDecimal;

/** timestamp는 UTC Unix epoch 밀리초이며 시간 단위 데이터도 날짜로 잘라내지 않는다. */
public record CryptoHistoryPoint(long timestamp, BigDecimal price) {
}
