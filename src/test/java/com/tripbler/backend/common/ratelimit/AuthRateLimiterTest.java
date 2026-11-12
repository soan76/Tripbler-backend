package com.tripbler.backend.common.ratelimit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import com.tripbler.backend.common.exception.ErrorCode;
import com.tripbler.backend.common.exception.TooManyRequestsException;

class AuthRateLimiterTest {

    private static final String IP = "10.0.0.1";
    private static final String EMAIL = "user@gmail.com";

    private final MutableClock clock = new MutableClock(Instant.parse("2026-11-12T00:00:00Z"));

    private AuthRateLimiter limiter(int capacity) {
        return new AuthRateLimiter(clock, capacity);
    }

    private long retryAfter(Runnable request) {
        TooManyRequestsException exception = assertThrows(TooManyRequestsException.class, request::run);
        assertEquals(ErrorCode.TOO_MANY_REQUESTS, exception.getErrorCode());
        return exception.getRetryAfterSeconds();
    }

    @Test
    void allowsUpToMaxThenRejectsWithRetryAfter() {
        AuthRateLimiter limiter = limiter(100);

        for (int i = 0; i < 10; i++) {
            limiter.acquire(RateLimitBucket.LOGIN_IP, IP);
        }

        assertEquals(60, retryAfter(() -> limiter.acquire(RateLimitBucket.LOGIN_IP, IP)));
    }

    @Test
    void passesExactlyAtAllowedTime() {
        AuthRateLimiter limiter = limiter(100);
        for (int i = 0; i < 10; i++) {
            limiter.acquire(RateLimitBucket.LOGIN_IP, IP);
        }
        clock.advance(Duration.ofSeconds(59));
        assertEquals(1, retryAfter(() -> limiter.acquire(RateLimitBucket.LOGIN_IP, IP)));

        clock.advance(Duration.ofSeconds(1));

        assertDoesNotThrow(() -> limiter.acquire(RateLimitBucket.LOGIN_IP, IP));
    }

    @Test
    void emailMinIntervalBoundary() {
        AuthRateLimiter limiter = limiter(100);
        limiter.acquireSendCode(IP, EMAIL);

        clock.advance(Duration.ofSeconds(59));
        assertEquals(1, retryAfter(() -> limiter.acquireSendCode("10.0.0.2", EMAIL)));

        clock.advance(Duration.ofSeconds(1));
        assertDoesNotThrow(() -> limiter.acquireSendCode("10.0.0.2", EMAIL));
    }

    @Test
    void emailHourlyLimitBoundary() {
        AuthRateLimiter limiter = limiter(100);
        // IP 한도(10분 5회)에 걸리지 않도록 매번 다른 IP를 쓴다.
        for (int i = 0; i < 5; i++) {
            limiter.acquireSendCode("10.0.1." + i, EMAIL);
            clock.advance(Duration.ofSeconds(60));
        }
        // 첫 발송은 5분 전, 60분이 되려면 55분 남음
        assertEquals(55 * 60, retryAfter(() -> limiter.acquireSendCode("10.0.2.1", EMAIL)));

        clock.advance(Duration.ofMinutes(55));
        assertDoesNotThrow(() -> limiter.acquireSendCode("10.0.2.1", EMAIL));
    }

    @Test
    void normalizesEmailAcrossFlows() {
        AuthRateLimiter limiter = limiter(100);
        List<String> variants = List.of(
            " User@Gmail.com ", "user@gmail.com", "USER@GMAIL.COM", "user@Gmail.com ", "\tuser@gmail.com"
        );
        for (int i = 0; i < variants.size(); i++) {
            limiter.acquireSendCode("10.0.3." + i, variants.get(i));
            clock.advance(Duration.ofSeconds(61));
        }

        retryAfter(() -> limiter.acquireSendCode("10.0.4.1", "user@gmail.com"));
    }

    @Test
    void sendCodeReturnsLongerWaitWhenBothLimited() {
        AuthRateLimiter limiter = limiter(100);
        // IP: 다른 이메일로 5회 → 10분 한도 소진
        for (int i = 0; i < 5; i++) {
            limiter.acquireSendCode(IP, "other" + i + "@gmail.com");
        }
        clock.advance(Duration.ofSeconds(30));
        // 이메일: 다른 IP로 한 번 보내 60초 간격 시작
        limiter.acquireSendCode("10.0.9.9", EMAIL);

        // IP는 570초, 이메일은 60초 남음 → 긴 쪽
        assertEquals(570, retryAfter(() -> limiter.acquireSendCode(IP, EMAIL)));
    }

    @Test
    void rejectionByOneLimitDoesNotConsumeTheOther() {
        AuthRateLimiter limiter = limiter(100);
        limiter.acquireSendCode("10.0.9.9", EMAIL);

        // 이메일 간격으로 거절 → IP 횟수는 그대로여야 한다
        retryAfter(() -> limiter.acquireSendCode(IP, EMAIL));
        for (int i = 0; i < 5; i++) {
            limiter.acquireSendCode(IP, "fresh" + i + "@gmail.com");
        }

        // IP 한도로 거절 → 새 이메일 횟수는 그대로여야 한다
        retryAfter(() -> limiter.acquireSendCode(IP, "untouched@gmail.com"));
        assertDoesNotThrow(() -> limiter.acquireSendCode("10.0.8.8", "untouched@gmail.com"));
    }

    @Test
    void bucketsAreIndependent() {
        AuthRateLimiter limiter = limiter(100);
        for (int i = 0; i < 10; i++) {
            limiter.acquire(RateLimitBucket.LOGIN_IP, IP);
        }

        assertDoesNotThrow(() -> limiter.acquire(RateLimitBucket.SESSION_IP, IP));
    }

    @Test
    void retryAfterRoundsUpSubSecond() {
        AuthRateLimiter limiter = limiter(100);
        for (int i = 0; i < 10; i++) {
            limiter.acquire(RateLimitBucket.LOGIN_IP, IP);
        }

        clock.advance(Duration.ofMillis(59_800));

        assertEquals(1, retryAfter(() -> limiter.acquire(RateLimitBucket.LOGIN_IP, IP)));
    }

    @Test
    void fullCapacityRejectsNewKeyUntilEarliestExpires() {
        AuthRateLimiter limiter = limiter(2);
        limiter.acquire(RateLimitBucket.LOGIN_IP, "a");
        clock.advance(Duration.ofSeconds(10));
        limiter.acquire(RateLimitBucket.LOGIN_IP, "b");

        assertEquals(50, retryAfter(() -> limiter.acquire(RateLimitBucket.LOGIN_IP, "c")));

        clock.advance(Duration.ofSeconds(50));
        assertDoesNotThrow(() -> limiter.acquire(RateLimitBucket.LOGIN_IP, "c"));

        // 기존 키 b의 기록은 유지된다: 9번 더 보내면 한도
        for (int i = 0; i < 9; i++) {
            limiter.acquire(RateLimitBucket.LOGIN_IP, "b");
        }
        retryAfter(() -> limiter.acquire(RateLimitBucket.LOGIN_IP, "b"));
    }

    @Test
    void concurrentSendCodeReservesOnce() throws Exception {
        AuthRateLimiter limiter = limiter(100);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Boolean>> futures = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                futures.add(executor.submit(() -> {
                    start.await();
                    try {
                        limiter.acquireSendCode(IP, EMAIL);
                        return true;
                    } catch (TooManyRequestsException exception) {
                        return false;
                    }
                }));
            }
            start.countDown();
            int successes = 0;
            for (Future<Boolean> future : futures) {
                if (future.get(10, TimeUnit.SECONDS)) {
                    successes++;
                }
            }
            assertEquals(1, successes);
        } finally {
            executor.shutdownNow();
        }
    }

    private static final class MutableClock extends Clock {

        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
