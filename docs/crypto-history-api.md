# 암호화폐 History API

`GET /api/v1/crypto/history?coin=BTC&currency=KRW&period=7D`

현재가와 History 모두 다음 코인을 KRW 기준으로 지원한다. 파라미터를 생략하면 BTC, KRW, 7D를 사용한다.
문자열 앞뒤 공백과 대소문자는 정규화한다. 심볼이 기본 요청값이며 기존 API ID 입력도 호환된다.

| 심볼 | 이름 | CoinGecko API ID |
| --- | --- | --- |
| BTC | Bitcoin | bitcoin |
| ETH | Ethereum | ethereum |
| SOL | Solana | solana |
| XRP | XRP | ripple |
| DOGE | Dogecoin | dogecoin |
| ADA | Cardano | cardano |
| DASH | Dash | dash |

CryptoCoin이 중앙 매핑을 관리한다. 미지원 코인은 외부 호출 전에 INVALID_REQUEST(400)로 거부한다.
예: GET /api/v1/crypto/price?coin=XRP&currency=KRW
공개 현재가/History 응답은 symbol만 제공하며 이전 coin(API ID) 필드는 제거했다.
입력의 bitcoin 등 기존 ID 별칭은 호환되지만 Flutter는 심볼을 사용해야 한다.

## 지원 코인 목록

GET /api/v1/crypto/coins는 인증 없이 지원 목록을 반환하며 외부 API를 호출하지 않는다.

```json
[
  {"symbol":"BTC","name":"Bitcoin"},
  {"symbol":"ETH","name":"Ethereum"},
  {"symbol":"SOL","name":"Solana"},
  {"symbol":"XRP","name":"XRP"},
  {"symbol":"DOGE","name":"Dogecoin"},
  {"symbol":"ADA","name":"Cardano"},
  {"symbol":"DASH","name":"Dash"}
]
```

Flutter는 이 목록으로 선택 UI를 만들고 선택된 symbol을 coin 요청 파라미터로 전달한다.
서비스와 캐시는 CryptoCoin 타입을 사용하며 CoinGecko ID 변환은 CoinGeckoCryptoMarketClient에서만 수행한다.
매핑 등록 시 서로 다른 코인 사이의 대소문자 무시 심볼/ID 중복 또는 교차 충돌은 초기화 오류로 처리한다.

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
  "symbol": "BTC",
  "currency": "KRW",
  "period": "7D",
  "days": 7,
  "prices": [
    { "timestamp": 1779027899041, "price": 100000001.25 },
    { "timestamp": 1779028199661, "price": 100000002.50 }
  ],
  "fetchedAt": "2026-09-28T12:00:00Z"
}
```

- timestamp: UTC Unix epoch 밀리초. 제공자가 반환한 시각을 보존한다.
- price: BigDecimal로 처리하는 해당 시각의 가격.
- prices: 시각 오름차순이며 중복 시각은 마지막 값을 사용한다.
- fetchedAt: 현재가/History 모두 UTC ISO-8601 형식(Z)의 조회 시각이다. 가격 갱신 시각이 아니며 캐시 조회 시 새로 갱신하지 않는다.
- 데이터 간격과 개수는 제공자 응답에 따른다. 날짜별 보간이나 누락값 생성은 하지 않는다.
- 빈 응답, 잘못된 데이터, 인증 실패, 호출 한도 초과 및 통신 실패는 기존 공통 형식의
  CRYPTO_PROVIDER_UNAVAILABLE(503)로 변환한다.

## 외부 API

`GET https://api.coingecko.com/api/v3/coins/bitcoin/market_chart?vs_currency=krw&days=7`

기존 coingecko.api-key를 x-cg-demo-api-key 헤더로 보낸다.
interval은 생략하여 Demo 자동 간격을 사용한다. 외부 market_caps/total_volumes는 공개 DTO에 포함하지 않는다.
CryptoService는 CryptoMarketClient 인터페이스에 의존한다.

공식 문서: https://docs.coingecko.com/demo/reference/coins-id-market-chart

## 접근 정책과 캐시

- GET /api/v1/crypto/**는 SecurityConfig에서 명시적으로 인증 없이 허용한다.
- 현재가 기본 TTL은 60초, History는 15분이며 외부 조회 성공 후부터 계산한다.
- crypto.cache.price-ttl=60s, crypto.cache.history-ttl=15m으로 설정할 수 있다.
  환경변수는 CRYPTO_CACHE_PRICE_TTL, CRYPTO_CACHE_HISTORY_TTL이다. 양수 Duration만 허용한다.
- 키는 제공자에 독립적인 CryptoCoin/currency이며 History에는 period가 추가된다.
  BTC와 bitcoin은 같은 캐시를 공유하고 다른 코인·기간은 별도로 저장한다.
- 같은 키의 동시 조회는 하나의 외부 호출 결과를 공유한다. 오류는 캐시하지 않으며 만료 데이터로 실패를 숨기지 않는다.
- 캐시는 서버 인스턴스의 메모리에 저장되고 재시작하면 초기화된다. 여러 서버 사이에서 공유되지 않는다.

## 검증

```powershell
.\gradlew.bat test --tests "com.tripbler.backend.crypto.*"
```

HTTP 모의 서버를 사용하므로 실제 API 키와 외부 네트워크 없이 검증한다.
