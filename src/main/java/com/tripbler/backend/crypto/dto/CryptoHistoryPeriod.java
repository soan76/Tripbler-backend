package com.tripbler.backend.crypto.dto;

/** 달력 월/연도가 아닌 고정 일수. Demo 한도에 맞춰 1Y도 최대 365일이다. */
public enum CryptoHistoryPeriod {
    SEVEN_DAYS("7D", 7),
    ONE_MONTH("1M", 30),
    THREE_MONTHS("3M", 90),
    SIX_MONTHS("6M", 180),
    ONE_YEAR("1Y", 365);

    private final String code;
    private final int days;

    CryptoHistoryPeriod(String code, int days) {
        this.code = code;
        this.days = days;
    }

    public String code() {
        return code;
    }

    public int days() {
        return days;
    }
}
