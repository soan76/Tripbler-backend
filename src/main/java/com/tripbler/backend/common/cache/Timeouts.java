package com.tripbler.backend.common.cache;

import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.concurrent.TimeoutException;

public final class Timeouts {
    private Timeouts() {}
    public static boolean isTimeout(Throwable failure) {
        for (int depth = 0; failure != null && depth < 20; depth++, failure = failure.getCause()) {
            if (failure instanceof SocketTimeoutException || failure instanceof HttpTimeoutException ||
                failure instanceof TimeoutException) return true;
        }
        return false;
    }
}
