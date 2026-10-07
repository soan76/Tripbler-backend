package com.tripbler.backend.common.cache;

import java.time.*;
import java.util.*;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

/** 성공값만 보관한다. 타임아웃 fallback은 원래 TTL과 조회 시각을 연장하지 않는다. */
public final class TimeoutFallbackCache<K, V> {
    private static final class Slot<V> {
        V value;
        Instant expiresAt;
        Instant retryAt = Instant.MIN;
    }
    private final Map<K, Slot<V>> slots = new LinkedHashMap<>(16, .75f, true);
    private final Clock clock;
    private final Duration ttl;
    private final Duration staleWindow;
    private final int capacity;

    public TimeoutFallbackCache(Clock clock, Duration ttl, Duration staleWindow, int capacity) {
        if (ttl.isNegative() || ttl.isZero() || staleWindow.isNegative() || capacity < 1) {
            throw new IllegalArgumentException("Invalid cache limits");
        }
        this.clock = clock; this.ttl = ttl; this.staleWindow = staleWindow; this.capacity = capacity;
    }

    private synchronized Slot<V> slot(K key) {
        Slot<V> found = slots.get(key);
        if (found != null) return found;
        if (slots.size() >= capacity) slots.remove(slots.keySet().iterator().next());
        Slot<V> created = new Slot<>();
        slots.put(key, created);
        return created;
    }

    public V get(K key, Supplier<V> loader, UnaryOperator<V> stale) {
        Slot<V> slot = slot(key);
        synchronized (slot) {
            Instant now = clock.instant();
            if (slot.value != null && now.isBefore(slot.expiresAt)) return slot.value;
            if (canFallback(slot, now) && now.isBefore(slot.retryAt)) return stale.apply(slot.value);
            try {
                V loaded = Objects.requireNonNull(loader.get());
                slot.value = loaded;
                slot.expiresAt = clock.instant().plus(ttl);
                slot.retryAt = Instant.MIN;
                return loaded;
            } catch (RuntimeException failure) {
                if (!Timeouts.isTimeout(failure) || !canFallback(slot, clock.instant())) throw failure;
                // 같은 키로 몰려온 요청이 연속으로 타임아웃을 기다리지 않도록 짧게 쉬고 재시도한다.
                slot.retryAt = clock.instant().plusSeconds(15);
                return stale.apply(slot.value);
            }
        }
    }

    private boolean canFallback(Slot<V> slot, Instant now) {
        return slot.value != null && now.isBefore(slot.expiresAt.plus(staleWindow));
    }
}
