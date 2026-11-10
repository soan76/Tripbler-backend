# Tripbler 백엔드 지침

## 목적과 범위
- Tripbler 여행 앱의 Spring Boot 4.1 / Java 17 API 서버다(PostgreSQL, JPA, Spring Security JWT).
- 연동 클라이언트는 `../tripbler`의 Flutter 앱이다. API 계약(경로, 요청·응답 필드)을 바꾸면 앱의 `lib/core/config/api_config.dart`와 호출부도 함께 확인한다.
- 설명과 작업 결과는 한국어로 간결하게 작성하고, 구현 완료·계획·미검증을 구분한다. 실행하지 않은 테스트나 실제 외부 API 호출을 성공했다고 표현하지 않는다.

## 테스트 규칙
- 동작을 바꾸면 관련 테스트를 실행해 검증한다. 영향이 넓으면 `./gradlew test`까지 범위를 넓힌다.
- 버그를 고치면 가능한 경우 재현 테스트를 추가한다.
- 새 기능에는 성공 경우와 주요 실패 경우의 테스트를 함께 추가한다. 컨트롤러는 HTTP 상태와 오류 응답, 서비스는 업무 규칙과 예외를 확인한다.
- 외부 API는 테스트에서 실제로 호출하지 않고 대역으로 대체한다.
- 테스트가 없는 코드를 바꾸면 최소한 `./gradlew compileJava`를 실행하고, 테스트가 없다고 보고한다.

## API 규칙
- 경로는 `/api/v1/<기능>` 형식을 따른다. 컨트롤러는 요청과 응답만 다루고, 업무 처리는 서비스, 외부 호출은 `*Client`에 둔다.
- 요청 본문은 DTO로 받고 `@Valid`와 검증 어노테이션으로 입력을 검사한다. 엔티티를 요청이나 응답으로 직접 쓰지 않는다.
- 업무 오류는 `BusinessException`과 `ErrorCode`로 던져 `ErrorResponse` 형식(`timestamp`, `status`, `code`, `message`, `path`)으로 응답한다.
- 새 엔드포인트의 인증 필요 여부를 정하고 `SecurityConfig`에 반영한다. 인증 없이 열 때는 이유를 보고한다.
- 응답 필드를 지우거나 이름·타입을 바꾸면 앱이 깨질 수 있다. 가능하면 필드 추가로 해결하고, 필요하면 앱의 변경 내용과 적용 순서를 함께 보고한다.

## 명령어
```bash
./gradlew bootRun
./gradlew build
./gradlew test
./gradlew test --tests "com.tripbler.backend.common.cache.TimeoutFallbackCacheTest"
./gradlew test --tests "*RefreshTokenServiceTest.someMethod"
```
- `src/main/resources/application.properties`에는 DB, JWT, 메일, CoinGecko 설정이 있다. 실행에는 PostgreSQL이 필요하다.
- Google 키는 이 파일에 없고 **환경 변수로 넣는다**. Spring 규칙에 따라 속성 이름이 다음 환경 변수에 대응한다.

  | 속성 | 환경 변수 |
  | --- | --- |
  | `google.places.api-key` | `GOOGLE_PLACES_APIKEY` |
  | `google.translation.api-key` | `GOOGLE_TRANSLATION_APIKEY` |
  | `google.oauth.client-id` | `GOOGLE_OAUTH_CLIENTID` |

## 아키텍처

| 기능 | 패키지 (`com.tripbler.backend.*`) | 외부 서비스 |
| --- | --- | --- |
| 국가 통화 환율 + 기간별 시세 | `exchange` | Frankfurter |
| 암호화폐 시세 + 기간별 시세 | `crypto` | CoinGecko |
| 장소 검색(로그인 필요) / 주변 장소 / 상세 | `place` | Google Places |
| 텍스트 번역 | `translation` | Google Translation |
| 인증, 회원, 구글 계정 연동, 프로필 이미지 | `auth`, `user` | 인증 코드 메일은 Gmail SMTP |

기능 패키지마다 `controller → service → client` 구조다. `*Client` 인터페이스와 외부 서비스별 구현체(예: `FrankfurterExchangeRateClient`)가 있고, `dto/`에 API 응답과 외부 응답 형식을 둔다. 외부 API 키는 서버에만 두며 앱으로 내려보내지 않는다. 공통 요소는 `common/`에 있다.
- `common/cache/TimeoutFallbackCache` + 기능별 `*ResponseCache`: 메모리 TTL 캐시. **외부 응답 시간 초과일 때만** 마지막 캐시 값을 `stale: true`와 원래 `fetchedAt`으로 돌려준다. 다른 오류는 성공으로 바꾸지 않는다. TTL, 허용 기간, 키 개수 제한, 15초 재시도 억제는 `docs/market-network-resilience.md`에 있다.
- `common/chart/HistoryDownsampler`: 기간별 시세 엔드포인트는 `maxPoints`(기본 300, 16~1000)를 받는다. 응답할 때만 샘플링하며 첫·마지막 점과 구간별 최저·최고점을 보존한다.
- `common/exception`: `BusinessException` + `ErrorCode` → `GlobalExceptionHandler` → `ErrorResponse`. 외부 서비스 실패는 전용 예외(`ExchangeProviderException`, `PlacesProviderException`, `TranslationProviderException`)를 쓴다.
- `common/config/SecurityConfig`: JWT 액세스 토큰을 쓰는 OAuth2 리소스 서버. 리프레시 토큰은 DB(`auth/RefreshToken`)에 저장하고, 인증 없이 열리는 엔드포인트는 여기서 지정한다.
- 추가 API 설명: `docs/crypto-history-api.md`, `docs/places-api.md`(장소 API 계약, 필드 마스크 비용 등급, 오류 기준).
