package com.tripbler.backend.common.exception;

public class ExchangeProviderException
    extends BusinessException {

    public ExchangeProviderException(int providerStatus) {
        super(
            ErrorCode.EXCHANGE_PROVIDER_UNAVAILABLE,
            "환율 제공 서비스 요청에 실패했습니다. "
                + "providerStatus="
                + providerStatus
        );
    }

    public ExchangeProviderException(Throwable cause) {
        super(
            ErrorCode.EXCHANGE_PROVIDER_UNAVAILABLE,
            "환율 제공 서비스에 연결할 수 없습니다.",
            cause
        );
    }
}