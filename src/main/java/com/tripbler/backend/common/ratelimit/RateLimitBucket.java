package com.tripbler.backend.common.ratelimit;

import java.time.Duration;

// 요청 제한 묶음. 같은 키라도 묶음이 다르면 따로 센다.
// 한도는 "최근 window 동안 maxRequests번"이고, minInterval이 있으면
// 마지막 요청 뒤 그 시간이 지나야 다시 허용한다.
public enum RateLimitBucket {

    LOGIN_IP(10, Duration.ofMinutes(1), Duration.ZERO),
    SEND_CODE_IP(5, Duration.ofMinutes(10), Duration.ZERO),
    SEND_CODE_EMAIL(5, Duration.ofMinutes(60), Duration.ofSeconds(60)),
    RECOVERY_SIGNUP_IP(30, Duration.ofMinutes(1), Duration.ZERO),
    SESSION_IP(60, Duration.ofMinutes(1), Duration.ZERO);

    private final int maxRequests;
    private final Duration window;
    private final Duration minInterval;

    RateLimitBucket(
        int maxRequests,
        Duration window,
        Duration minInterval
    ) {
        this.maxRequests = maxRequests;
        this.window = window;
        this.minInterval = minInterval;
    }

    public int maxRequests() {
        return maxRequests;
    }

    public Duration window() {
        return window;
    }

    public Duration minInterval() {
        return minInterval;
    }

    // 마지막 기록 뒤 이 시간이 지나면 키의 기록이 모두 의미를 잃는다.
    Duration retention() {
        return window.compareTo(minInterval) >= 0 ? window : minInterval;
    }
}
