package com.tripbler.backend.common.ratelimit;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.tripbler.backend.common.exception.TooManyRequestsException;

/**
 * 공개 인증 API의 요청 횟수를 서버 메모리에서 센다(서버 한 대 기준, 재시작하면 초기화).
 *
 * 모든 판정과 기록은 하나의 락 안에서 한다. 그래서 여러 한도를 함께 확인할 때
 * "모두 허용일 때만 모두 기록"이 원자적으로 지켜지고, 동시에 들어온 요청도 한 번씩만 센다.
 *
 * 빈 등록은 WebConfig의 @Import로만 한다(컴포넌트 스캔 대상이 아니다).
 */
public class AuthRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(AuthRateLimiter.class);

    private static final int DEFAULT_CAPACITY_PER_BUCKET = 10_000;

    private final Clock clock;
    private final int capacityPerBucket;
    private final Map<RateLimitBucket, Map<String, ArrayDeque<Instant>>> records =
        new EnumMap<>(RateLimitBucket.class);

    @Autowired
    public AuthRateLimiter() {
        this(Clock.systemUTC(), DEFAULT_CAPACITY_PER_BUCKET);
    }

    AuthRateLimiter(Clock clock, int capacityPerBucket) {
        this.clock = clock;
        this.capacityPerBucket = capacityPerBucket;
        for (RateLimitBucket bucket : RateLimitBucket.values()) {
            records.put(bucket, new HashMap<>());
        }
    }

    // 한 묶음의 한도를 확인하고, 허용되면 기록한다. 거절되면 TooManyRequestsException.
    public void acquire(RateLimitBucket bucket, String key) {
        tryAcquire(List.of(new Key(bucket, key)));
    }

    // 인증 메일 발송: IP 한도와 이메일 한도를 함께 판정하고, 둘 다 허용될 때만 둘 다 기록한다.
    public void acquireSendCode(String clientIp, String email) {
        tryAcquire(List.of(
            new Key(RateLimitBucket.SEND_CODE_IP, clientIp),
            new Key(RateLimitBucket.SEND_CODE_EMAIL, normalizeEmail(email))
        ));
    }

    public static String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private synchronized void tryAcquire(List<Key> keys) {
        Instant now = clock.instant();
        Duration longestWait = Duration.ZERO;

        for (Key key : keys) {
            Duration wait = waitFor(key, now);
            if (wait.compareTo(longestWait) > 0) {
                longestWait = wait;
            }
        }

        if (!longestWait.isZero()) {
            throw new TooManyRequestsException(toRetryAfterSeconds(longestWait));
        }

        for (Key key : keys) {
            records.get(key.bucket())
                .computeIfAbsent(key.value(), ignored -> new ArrayDeque<>())
                .addLast(now);
        }
    }

    // 이 키가 지금 허용되려면 얼마나 기다려야 하는지 계산한다. 0이면 허용.
    private Duration waitFor(Key key, Instant now) {
        RateLimitBucket bucket = key.bucket();
        Map<String, ArrayDeque<Instant>> bucketRecords = records.get(bucket);
        ArrayDeque<Instant> history = bucketRecords.get(key.value());

        if (history == null) {
            return waitForNewKey(bucket, bucketRecords, now);
        }

        // 최근 window 안의 기록만 남긴다. 허용 시각과 정확히 같으면 통과하도록 경계는 제외한다.
        Instant windowStart = now.minus(bucket.window());
        while (!history.isEmpty() && !history.peekFirst().isAfter(windowStart)) {
            history.pollFirst();
        }

        Duration wait = Duration.ZERO;

        if (history.size() >= bucket.maxRequests()) {
            wait = positive(Duration.between(now, history.peekFirst().plus(bucket.window())));
        }

        if (!bucket.minInterval().isZero() && !history.isEmpty()) {
            Duration intervalWait =
                positive(Duration.between(now, history.peekLast().plus(bucket.minInterval())));
            if (intervalWait.compareTo(wait) > 0) {
                wait = intervalWait;
            }
        }

        return wait;
    }

    // 처음 보는 키: 자리가 있으면 허용. 가득 찼으면 만료된 키를 지우고,
    // 그래도 가득이면 기존 키의 제한은 그대로 두고 가장 먼저 자리가 나는 시각까지 기다리게 한다.
    private Duration waitForNewKey(
        RateLimitBucket bucket,
        Map<String, ArrayDeque<Instant>> bucketRecords,
        Instant now
    ) {
        if (bucketRecords.size() < capacityPerBucket) {
            return Duration.ZERO;
        }

        Instant expiredBefore = now.minus(bucket.retention());
        Iterator<ArrayDeque<Instant>> iterator = bucketRecords.values().iterator();
        while (iterator.hasNext()) {
            ArrayDeque<Instant> history = iterator.next();
            if (history.isEmpty() || !history.peekLast().isAfter(expiredBefore)) {
                iterator.remove();
            }
        }

        if (bucketRecords.size() < capacityPerBucket) {
            return Duration.ZERO;
        }

        Instant earliestFree = null;
        for (ArrayDeque<Instant> history : bucketRecords.values()) {
            Instant freeAt = history.peekLast().plus(bucket.retention());
            if (earliestFree == null || freeAt.isBefore(earliestFree)) {
                earliestFree = freeAt;
            }
        }

        log.warn("요청 제한 키 상한 도달: bucket={}, capacity={}", bucket, capacityPerBucket);

        return positive(Duration.between(now, earliestFree));
    }

    private static Duration positive(Duration duration) {
        return duration.isNegative() ? Duration.ZERO : duration;
    }

    private static long toRetryAfterSeconds(Duration wait) {
        long millis = wait.toMillis();
        long seconds = (millis + 999) / 1000;
        return Math.max(1, seconds);
    }

    private record Key(RateLimitBucket bucket, String value) {
    }
}
