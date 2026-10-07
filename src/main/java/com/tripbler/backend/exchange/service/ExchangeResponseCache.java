package com.tripbler.backend.exchange.service;

import java.time.*;
import java.util.List;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;
import com.tripbler.backend.common.cache.TimeoutFallbackCache;
import com.tripbler.backend.exchange.dto.*;

@Component
public class ExchangeResponseCache {
    private record LatestKey(String base, List<String> targets) {}
    private record HistoryKey(String base, String target, LocalDate start, LocalDate end) {}
    private final TimeoutFallbackCache<LatestKey, ExchangeRateResponse> latest;
    private final TimeoutFallbackCache<HistoryKey, HistoricalRateResponse> histories;

    public ExchangeResponseCache() { this(Clock.systemUTC()); }
    ExchangeResponseCache(Clock clock) {
        latest = new TimeoutFallbackCache<>(clock, Duration.ofMinutes(5), Duration.ofDays(3), 256);
        histories = new TimeoutFallbackCache<>(clock, Duration.ofHours(1), Duration.ofDays(7), 512);
    }
    public ExchangeRateResponse latest(String base, List<String> targets, Supplier<ExchangeRateResponse> loader) {
        return latest.get(new LatestKey(base, targets.stream().sorted().toList()), loader, ExchangeRateResponse::asStale);
    }
    public HistoricalRateResponse history(String base, String target, LocalDate start, LocalDate end,
                                           Supplier<HistoricalRateResponse> loader) {
        return histories.get(new HistoryKey(base, target, start, end), loader, HistoricalRateResponse::asStale);
    }
}
