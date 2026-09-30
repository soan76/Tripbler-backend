package com.tripbler.backend.crypto.domain;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import com.tripbler.backend.common.exception.BusinessException;
import com.tripbler.backend.common.exception.ErrorCode;

/** 사용자 심볼과 외부 API ID의 단일 매핑. 코인 추가는 이 목록에서 관리한다. */
public enum CryptoCoin {
    BTC("Bitcoin", "bitcoin"),
    ETH("Ethereum", "ethereum"),
    SOL("Solana", "solana"),
    XRP("XRP", "ripple"),
    DOGE("Dogecoin", "dogecoin"),
    ADA("Cardano", "cardano"),
    DASH("Dash", "dash");

    private static final Map<String, CryptoCoin> LOOKUP;
    static {
        Map<String, CryptoCoin> lookup = new HashMap<>();
        for (CryptoCoin coin : values()) {
            registerAlias(lookup, coin.symbol(), coin);
            registerAlias(lookup, coin.coinGeckoId, coin);
        }
        LOOKUP = Map.copyOf(lookup);
    }

    private final String displayName;
    private final String coinGeckoId;

    CryptoCoin(String displayName, String coinGeckoId) {
        this.displayName = displayName;
        this.coinGeckoId = coinGeckoId;
    }

    public String symbol() { return name(); }
    public String displayName() { return displayName; }
    public String coinGeckoId() { return coinGeckoId; }

    // 심볼/ID 간 교차 충돌도 검증한다. DASH/dash처럼 같은 코인의 별칭은 허용한다.
    static void registerAlias(Map<String, CryptoCoin> lookup, String alias, CryptoCoin coin) {
        if (alias == null || alias.isBlank()) {
            throw new IllegalStateException("코인 매핑의 별칭은 비어 있을 수 없습니다.");
        }
        String key = alias.trim().toLowerCase(Locale.ROOT);
        CryptoCoin previous = lookup.putIfAbsent(key, coin);
        if (previous != null && previous != coin) {
            throw new IllegalStateException("중복 코인 매핑: " + alias);
        }
    }

    /** 심볼이 기본 입력이며 기존 API ID 입력도 동일한 코인으로 정규화한다. */
    public static CryptoCoin resolve(String value) {
        CryptoCoin coin = value == null ? null : LOOKUP.get(value.trim().toLowerCase(Locale.ROOT));
        if (coin == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "지원하지 않는 암호화폐입니다.");
        }
        return coin;
    }
}
