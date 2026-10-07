# 느린 외부 응답과 History 전송 최적화

| 데이터 | 정상 TTL | TTL 만료 후 타임아웃 fallback 허용 기간 |
|---|---|---|
| Crypto 현재가 | 60초 | 10분 |
| Crypto History | 15분 | 24시간 |
| 국가 통화 환율 | 5분 | 3일 |
| 국가 통화 History | 1시간 | 7일 |

Crypto 설정은 `crypto.cache.price-ttl`, `history-ttl`, `price-stale-window`, `history-stale-window`로 변경한다.
캐시는 서버 인스턴스 메모리에 저장한다. 재시작 시 비워지며 서버 간 공유되지 않는다.
국가 통화는 현재가 256개/History 512개, Crypto는 각각 최대 64개 키로 제한한다.

timeout 원인만 fallback 대상이다. HTTP 인증 오류, 잘못된 응답, 유효성 오류 등은 성공으로 바꾸지 않는다.
CoinGecko 타임아웃은 안전한 종류 정보만 보존하고 외부 URL/키/본문이 포함될 수 있는 원본 예외는 전달하지 않는다.
fallback 응답은 `stale: true`, 정상 응답은 `stale: false`이며, 원래 `fetchedAt`을 보존한다.
fallback 후 15초 동안 같은 키의 재시도 폭주를 억제한다. 이 기간은 원래 만료/fallback 기한을 연장하지 않는다.
캐시 키에는 코인/통화 또는 기준·대상 통화/기간(법정 통화는 시작·종료일)이 포함된다.

## History 응답 축소

두 History 엔드포인트에 `maxPoints`(기본 300, 허용 16~1000)를 추가했다.
원본 조회/캐시 데이터는 유지하고 REST 응답에서만 샘플링한다.
첫/마지막 점과 구간별 최저·최고점을 보존한다. 값이나 타임스탬프를 만들어내지 않으며 반환 순서는 오름차순이다.
축소된 데이터는 표시용이며 원본의 모든 중간 포인트를 포함하지 않는다.

## 압축

`server.compression.enabled=true`, JSON MIME 타입, 최소 1024바이트로 설정했다.
클라이언트가 gzip을 허용할 때만 압축하고, 표준 HTTP 클라이언트의 자동 해제를 사용한다.
`CompressionTest`는 외부 API/DB 없이 실제 embedded HTTP 서버를 띄워 gzip 헤더, 압축 크기, 해제한 본문의 동일성을 검증한다.

```powershell
.\gradlew.bat test --tests "com.tripbler.backend.crypto.*" --tests "com.tripbler.backend.exchange.*" --tests "com.tripbler.backend.common.cache.*" --tests "com.tripbler.backend.common.chart.*"
```

외부 CoinGecko/Frankfurter 실통신이나 배포된 프록시의 압축 설정까지 검증하는 테스트는 아니다.
