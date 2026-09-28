package com.tripbler.backend.crypto.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** 기존 coingecko.api-key 설정(환경변수 참조 포함)을 바인딩한다. */
@Component
@ConfigurationProperties(prefix = "coingecko")
public class CoinGeckoProperties {
    private String apiKey = "";

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }
}
