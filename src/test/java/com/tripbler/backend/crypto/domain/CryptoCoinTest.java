package com.tripbler.backend.crypto.domain;

import static org.junit.jupiter.api.Assertions.*;
import java.util.HashMap;
import org.junit.jupiter.api.Test;

class CryptoCoinTest {
    @Test
    void duplicateIdsAndCrossSymbolCollisionsFailFast() {
        var lookup = new HashMap<String, CryptoCoin>();
        CryptoCoin.registerAlias(lookup, "bitcoin", CryptoCoin.BTC);
        assertThrows(IllegalStateException.class,
            () -> CryptoCoin.registerAlias(lookup, " BITCOIN ", CryptoCoin.ETH));
        CryptoCoin.registerAlias(lookup, "BTC", CryptoCoin.BTC);
        assertThrows(IllegalStateException.class,
            () -> CryptoCoin.registerAlias(lookup, "btc", CryptoCoin.SOL));
        assertThrows(IllegalStateException.class,
            () -> CryptoCoin.registerAlias(lookup, " ", CryptoCoin.SOL));
    }

    @Test
    void sameCoinMayHaveMatchingSymbolAndId() {
        var lookup = new HashMap<String, CryptoCoin>();
        CryptoCoin.registerAlias(lookup, "DASH", CryptoCoin.DASH);
        CryptoCoin.registerAlias(lookup, "dash", CryptoCoin.DASH);
        assertEquals(1, lookup.size());
        for (CryptoCoin coin : CryptoCoin.values()) {
            assertSame(coin, CryptoCoin.resolve(coin.symbol()));
            assertSame(coin, CryptoCoin.resolve(coin.coinGeckoId()));
        }
    }
}
