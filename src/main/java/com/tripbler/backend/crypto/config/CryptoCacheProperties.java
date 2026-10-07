package com.tripbler.backend.crypto.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** 현재가와 History의 캐시 수명을 독립적으로 설정한다. */
@Component
@ConfigurationProperties(prefix = "crypto.cache")
public class CryptoCacheProperties {
    private Duration priceStaleWindow = Duration.ofMinutes(10);
    private Duration historyStaleWindow = Duration.ofHours(24);
    public Duration getPriceStaleWindow() { return priceStaleWindow; }
    public Duration getHistoryStaleWindow() { return historyStaleWindow; }
    public void setPriceStaleWindow(Duration value) { priceStaleWindow = positive(value); }
    public void setHistoryStaleWindow(Duration value) { historyStaleWindow = positive(value); }
    private Duration priceTtl = Duration.ofSeconds(60);
    private Duration historyTtl = Duration.ofMinutes(15);

    public Duration getPriceTtl() { return priceTtl; }
    public Duration getHistoryTtl() { return historyTtl; }

    public void setPriceTtl(Duration value) { priceTtl = positive(value); }
    public void setHistoryTtl(Duration value) { historyTtl = positive(value); }

    private Duration positive(Duration value) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException("암호화폐 캐시 TTL은 0보다 커야 합니다.");
        }
        return value;
    }
}
