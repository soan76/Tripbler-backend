# 암호화폐 History API

`GET /api/v1/crypto/history?coin=bitcoin&currency=KRW&period=7D`

현재 지원하는 코인/통화는 bitcoin/KRW이다. 파라미터를 생략하면 bitcoin, KRW, 7D를 사용한다.
문자열 앞뒤 공백과 대소문자는 정규화한다.

| period | 조회 일수 | CoinGecko 자동 간격 |
| --- | ---: | --- |
| 7D | 7 | 시간 단위 |
| 1M | 30 | 시간 단위 |
| 3M | 90 | 시간 단위 |
| 6M | 180 | 일 단위 |
| 1Y | 365 | 일 단위 |

월/연도는 달력 기준이 아닌 고정 일수이다. 윤년에도 1Y는 365일이다.
2Y, 5Y, max 및 임의 일수는 지원하지 않으며 INVALID_REQUEST(400)를 반환한다.

## 응답 예시

가격과 조회 시각은 설명용이다.

```json
{
  "coin": "bitcoin",
  "currency": "KRW",
  "period": "7D",
  "days": 7,
  "prices": [
    { "timestamp": 1779027899041, "price": 100000001.25 },
    { "timestamp": 1779028199661, "price": 100000002.50 }
  ],
  "fetchedAt": "2026-09-28T12:00:00"
}
```

- timestamp: UTC Unix epoch 밀리초. 제공자가 반환한 시각을 보존한다.
- price: BigDecimal로 처리하는 해당 시각의 가격.
- prices: 시각 오름차순이며 중복 시각은 마지막 값을 사용한다.
- fetchedAt: 기존 현재가 API와 같은 서버 로컬 조회 시각. 가격 갱신 시각이 아니다.
- 데이터 간격과 개수는 제공자 응답에 따른다. 날짜별 보간이나 누락값 생성은 하지 않는다.
- 빈 응답, 잘못된 데이터, 인증 실패, 호출 한도 초과 및 통신 실패는 기존 공통 형식의
  CRYPTO_PROVIDER_UNAVAILABLE(503)로 변환한다.

## 외부 API

`GET https://api.coingecko.com/api/v3/coins/bitcoin/market_chart?vs_currency=krw&days=7`

기존 coingecko.api-key를 x-cg-demo-api-key 헤더로 보낸다.
interval은 생략하여 Demo 자동 간격을 사용한다. 외부 market_caps/total_volumes는 공개 DTO에 포함하지 않는다.
CryptoService는 CryptoMarketClient 인터페이스에 의존한다.

공식 문서: https://docs.coingecko.com/demo/reference/coins-id-market-chart

## 검증

```powershell
.\gradlew.bat test --tests "com.tripbler.backend.crypto.*"
```

HTTP 모의 서버를 사용하므로 실제 API 키와 외부 네트워크 없이 검증한다.
