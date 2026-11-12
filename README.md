# Tripbler Backend

여행 도구 앱 **Tripbler**의 API 서버입니다. 환율, 암호화폐 시세, 장소 검색, 번역 같은 외부 서비스를 한곳에서 감싸고, 회원·인증 기능을 제공합니다.

환율·암호화폐·장소 검색·번역 요청은 Tripbler 서버를 거치며, 이 외부 서비스의 API 키는 서버에만 둡니다. 지도 표시용 Google Maps SDK 키는 Android와 iOS 앱에서 별도로 설정합니다.

- 클라이언트(Flutter 앱): [soan76/Tripbler-app](https://github.com/soan76/Tripbler-app)

## 기술 스택

| 구분 | 사용 기술 |
| --- | --- |
| 언어 / 프레임워크 | Java 17, Spring Boot 4.1 (Web MVC, RestClient, Validation) |
| 데이터 | PostgreSQL, Spring Data JPA |
| 인증 | Spring Security, OAuth2 Resource Server(JWT 액세스 토큰), DB 저장 Refresh Token |
| 외부 연동 | Frankfurter(환율), CoinGecko(암호화폐), Google Places, Google Translation, Google 계정(ID 토큰 검증), Gmail SMTP(인증 코드 메일) |
| 빌드 / 테스트 | Gradle, JUnit 5, Spring Security Test |

## 주요 기능

- **환율**: 국가 통화 환율 조회와 기간별 시세(차트용)
- **암호화폐**: 지원 코인 목록, 현재가, 기간별 시세(CoinGecko)
- **장소**: 텍스트 검색(로그인 필요), 주변 장소, 가장 가까운 장소, 장소 상세(Google Places)
- **번역**: 텍스트 번역(Google Translation). 앱은 기기에서 OCR을 수행하고 인식된 텍스트만 보냅니다.
- **회원 / 인증**: 회원가입, 로그인, 토큰 재발급, 로그아웃, 이메일 인증 코드로 아이디 찾기·비밀번호 재설정, 닉네임·비밀번호 변경, 프로필 이미지 업로드·삭제, 구글 계정 연동·해제, 회원 탈퇴

### 시세 응답 안정성

- **시간 초과 대비 캐시**: 기능별 메모리 TTL 캐시를 둡니다. 외부 API가 **시간 초과**일 때만 마지막 캐시 값을 `stale: true`와 원래 `fetchedAt`으로 돌려주고, 다른 오류는 성공으로 바꾸지 않습니다.
- **차트 다운샘플링**: 기간별 시세 API는 `maxPoints`(기본 300, 16~1000)를 받아 응답할 때만 점 개수를 줄입니다. 첫·마지막 점과 구간별 최저·최고점은 보존합니다.
- 큰 JSON 응답은 gzip으로 압축합니다.

## API 개요

모든 경로는 `/api/v1` 아래에 있습니다.

| 기능 | 메서드 · 경로 | 인증 |
| --- | --- | --- |
| 헬스 체크 | `GET /health` | 공개 |
| 환율 | `GET /exchange/rates`, `GET /exchange/history` | 공개 |
| 암호화폐 | `GET /crypto/coins`, `GET /crypto/price`, `GET /crypto/history` | 공개 |
| 장소 검색 | `GET /places/search` | **필요** |
| 장소 조회 | `GET /places/nearby`, `GET /places/nearest`, `GET /places/{placeId}` | 공개 |
| 번역 | `POST /translation` | 공개 |
| 회원가입 / 아이디 중복 확인 | `POST /users`, `GET /users/check-login-id` | 공개 |
| 인증 | `POST /auth/login`, `/auth/refresh`, `/auth/logout`, `/auth/find-id/send-code`, `/auth/find-id/verify-code`, `/auth/password-reset/send-code` → `/auth/password-reset/verify-code` → `/auth/password-reset` | 공개 |
| 내 정보 | `GET·DELETE /users/me`, `PATCH /users/me/nickname`, `PATCH /users/me/password`, `PUT·DELETE /users/me/profile-image` | **필요** |
| 소셜 계정 | `GET /users/me/social-accounts`, `POST·DELETE /users/me/social-accounts/google` | **필요** |
| 관리자 | `/admin/**` | `ADMIN` 권한 |

- 인증이 필요한 API는 `Authorization: Bearer <access token>` 헤더를 붙입니다.
- 로그아웃은 Access Token 없이 본문의 `refreshToken`으로 호출하며, 일치하는 토큰만 폐기합니다.
- 공개 인증·가입 API는 IP별, 인증 메일 발송은 IP·이메일별로 요청 횟수를 제한합니다. 한도를 넘으면 `429`와 `Retry-After` 헤더로 응답합니다. 인증코드는 5번 틀리면 폐기됩니다.
- 오류는 모두 같은 형식으로 응답합니다.

  ```json
  { "timestamp": "...", "status": 400, "code": "...", "message": "...", "path": "/api/v1/..." }
  ```

자세한 계약은 `docs/`를 참고하세요.

| 문서 | 내용 |
| --- | --- |
| [docs/places-api.md](docs/places-api.md) | 장소 API 계약, 필드 마스크 비용 등급, 오류 기준 |
| [docs/crypto-history-api.md](docs/crypto-history-api.md) | 암호화폐 기간별 시세 API |
| [docs/market-network-resilience.md](docs/market-network-resilience.md) | 캐시 TTL, 허용 기간, 재시도 억제 등 시세 안정성 정책 |
| [docs/auth-logout-api.md](docs/auth-logout-api.md) | 로그아웃 계약 |
| [docs/auth-rate-limit-api.md](docs/auth-rate-limit-api.md) | 공개 인증 API 요청 제한, 인증코드 오답 제한 |

## 프로젝트 구조

```
src/main/java/com/tripbler/backend
├── exchange      # 환율 (Frankfurter)
├── crypto        # 암호화폐 시세 (CoinGecko)
├── place         # 장소 (Google Places)
├── translation   # 번역 (Google Translation)
├── auth          # Refresh Token
├── user          # 회원, 인증 API, 소셜 계정, 프로필 이미지
├── admin
└── common        # 보안 설정, 예외 처리, 캐시, 차트 다운샘플링
```

기능 패키지는 `controller → service → client` 구조입니다.

- 컨트롤러는 요청·응답만 다루고, 업무 처리는 서비스, 외부 호출은 `*Client` 인터페이스와 서비스별 구현체(예: `FrankfurterExchangeRateClient`)가 맡습니다.
- 요청·응답은 DTO로 주고받고 `@Valid`로 입력을 검증합니다. 엔티티는 API에 직접 노출하지 않습니다.
- 업무 오류는 `BusinessException` + `ErrorCode`로 던지고 `GlobalExceptionHandler`가 `ErrorResponse`로 바꿉니다.

## 실행 방법

### 준비물

- JDK 17
- PostgreSQL

### 설정

DB, JWT, 메일, CoinGecko, 프로필 이미지 저장 위치 설정은 `src/main/resources/application.properties`에 있습니다. 비밀값은 이 파일에 적지 않고 환경 변수로 읽습니다.

**필수 환경 변수** (기본값이 없어 설정하지 않으면 서버가 시작되지 않습니다)

| 환경 변수 | 대응 속성 | 용도 |
| --- | --- | --- |
| `POSTGRES_PASSWORD` | `spring.datasource.password` | PostgreSQL 비밀번호 |
| `JWT_SECRET` | `security.jwt.secret` | JWT 서명 키 |
| `COINGECKO_API_KEY` | `coingecko.api-key` | CoinGecko API 키 |
| `MAIL_USERNAME` | `spring.mail.username` | 인증 코드 메일을 보낼 Gmail 계정 |
| `MAIL_APP_PASSWORD` | `spring.mail.password` | Gmail 앱 비밀번호 |
| `GOOGLE_PLACES_APIKEY` | `google.places.api-key` | Google Places API 키 |
| `GOOGLE_TRANSLATION_APIKEY` | `google.translation.api-key` | Google Translation API 키 |
| `GOOGLE_OAUTH_CLIENTID` | `google.oauth.client-id` | Google 계정 로그인 클라이언트 ID |

Google 관련 속성은 `application.properties`에 없고, Spring 규칙에 따라 위 환경 변수 이름으로 들어갑니다.

**선택 환경 변수** (설정하지 않으면 기본값 사용)

| 환경 변수 | 기본값 |
| --- | --- |
| `PROFILE_IMAGE_STORAGE_TYPE` | `local` |
| `PROFILE_IMAGE_LOCAL_ROOT` | `./uploads` |
| `PROFILE_IMAGE_PUBLIC_BASE_URL` | `http://localhost:8080/uploads` |

DB 주소와 사용자 이름은 `application.properties`의 `spring.datasource.url`, `spring.datasource.username` 값을 씁니다.

### 명령어

macOS / Linux

```bash
./gradlew bootRun     # 서버 실행
./gradlew build       # 빌드
./gradlew test        # 전체 테스트
./gradlew test --tests "com.tripbler.backend.common.cache.TimeoutFallbackCacheTest"   # 클래스 하나
```

Windows (PowerShell)

```powershell
.\gradlew.bat bootRun
.\gradlew.bat test
```

- 외부 API는 테스트에서 실제로 호출하지 않고 대역으로 대체합니다.
- DB를 쓰는 테스트(`TripblerBackendApplicationTests`, 저장소 테스트)는 로컬 PostgreSQL이 필요합니다.

## 관련 저장소

- [Tripbler-app](https://github.com/soan76/Tripbler-app): Flutter 클라이언트. API 경로는 앱의 `lib/core/config/api_config.dart`에 모여 있습니다.
