# 장소 API 호출 제한 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 검색·상세를 로그인 사용자별 호출 제한으로 묶고, 주변 조회는 막고 최근접 조회는 지워, 한도를 넘은 요청이 Google을 호출하지 않게 한다.

**Architecture:** 앱이 먼저 상세 요청에 토큰을 보내고 진행 중인 같은 검색을 다시 보내지 않게 한다. 백엔드는 기존 `AuthRateLimiter`에 장소 버킷과 여러 창을 한 번에 판정하는 메서드를 더하고, `PlaceService`가 클라이언트 호출 전에 JWT `sub` 기준으로 한도를 확인한다. `SecurityConfig`는 `nearby`·`nearest`를 `denyAll()`, 검색·상세를 `authenticated()`로 둔다.

**Tech Stack:** Flutter/Provider(앱 `../tripbler`), Spring Boot 4.1 / Java 17, Spring Security JWT, JUnit 5, Mockito, MockMvc.

**Spec:** `docs/superpowers/specs/2026-11-12-places-rate-limit-design.md`

## Global Constraints
- 작업은 `main`에서 바로 한다(브랜치 금지). 커밋은 사용자가 요청할 때만 한다. 각 Task의 커밋 단계는 "사용자 요청 시".
- 적용 순서: Task 1·2(앱) → Task 3~5(백엔드). 상세를 로그인 필수로 바꾸는 Task 5는 Task 1 뒤에만.
- 완료 조건: 한도를 넘은 요청은 `429 TOO_MANY_REQUESTS` + `Retry-After`, 그 요청으로 Google 클라이언트(`PlaceClient`) 호출 0회.
- 숫자는 개발 초기 설정값(운영 보장값 아님): 검색 사용자별 1분 10회·1시간 100회, 상세 사용자별 1분 20회·1시간 200회. 검색과 상세는 따로 센다.
- 사용자 키: JWT `sub`(`@AuthenticationPrincipal Jwt` → `getSubject()`).
- 서버 결과 캐시를 추가하지 않는다. 앱의 30분 상세 캐시는 바꾸지 않는다.
- 새 라이브러리 금지. 백엔드 오류 응답은 기존 `ErrorResponse`.
- 앱 검증: 변경 Dart 파일마다 `dart format <파일>`, `flutter analyze --no-pub <파일>`, 관련 `flutter test --no-pub <경로>`. 앱 작업 전 `../tripbler/AGENTS.md`를 따른다.

## Review Focus
- 상세 카드를 연 채 로그아웃된 뒤 "평점·영업 정보"를 누르면 요청을 보내지 않고 예외 없이 끝나야 한다 → Task 1 `상세 조회는 로그인이 풀린 뒤에는 요청하지 않는다`.
- 진행 중이던 같은 검색이 실패한 직후 같은 검색(또는 다시 시도)은 다시 보내져야 한다 → Task 2 `진행 중이던 같은 검색이 끝나면 같은 검색을 다시 보낸다`.
- 검색이 429를 받으면 로그인 상태가 유지되고 서버 메시지가 보여야 한다 → Task 2 `검색 429는 로그인을 유지하고 서버 메시지를 보여 준다`.
- 끝 슬래시 변형(`/api/v1/places/nearby/`, `/nearest/`)도 어떤 핸들러에도 닿지 않고 Google 호출 0회여야 한다 → Task 5 `끝_슬래시_변형도_서비스를_호출하지_않는다`.
- 입력 검증에 실패한 검색(400)은 한도를 쓰지 않아야 한다 → Task 4 `검증에_실패한_검색은_한도를_쓰지_않는다`.

---

### Task 1: 앱 — 상세 요청에 토큰 첨부와 인증 실패 처리

**Files:**
- Modify: `../tripbler/lib/services/place_api_service.dart` (`fetchPlaceDetails`)
- Modify: `../tripbler/lib/providers/map_provider.dart` (`loadSelectedPlaceDetails`)
- Modify: `../tripbler/test/providers/map_provider_search_test.dart` (`FakePlaceApiService.fetchPlaceDetails`, "상세 조회" 그룹)
- Create: `../tripbler/test/services/place_api_service_details_test.dart`

**Interfaces:**
- Produces: `Future<PlaceModel> fetchPlaceDetails({required String placeId, required String authorizationHeader})`. 실패는 검색과 같은 `ApiException`(`statusCode`, `code`, `message`; 시간 초과·연결 실패는 `statusCode == null`)으로 던진다. 오류 본문 해석은 검색의 기존 helper를 재사용한다(이름을 일반화해도 된다). `PlaceApiException`은 상세 경로에서 더 쓰지 않는다.

- [ ] **Step 1: 실패하는 테스트 작성**
  - `place_api_service_details_test.dart`(MockClient):
    - `상세 요청에 Authorization 헤더를 붙인다`: 요청 헤더 `Authorization == 'Bearer t'`.
    - `상세 401은 statusCode 401인 ApiException`.
    - `상세 429는 서버 message를 담은 ApiException`: 본문 `{"status":429,"code":"TOO_MANY_REQUESTS","message":"요청이 너무 많습니다. 잠시 후 다시 시도해 주세요."}` → `statusCode == 429`, `message` 같음.
    - `상세 시간 초과는 statusCode가 null인 ApiException`.
  - `map_provider_search_test.dart` "상세 조회" 그룹(가짜 runner 사용):
    - `상세 조회는 authorizedRequest를 거쳐 토큰을 붙여 요청한다`: 가짜 서비스가 받은 `authorizationHeader`가 runner가 준 값.
    - `상세 조회 세션 만료는 만료 안내를 띄우고 카드를 실패로 두지 않는다`: runner가 `SessionExpiredException` → `hasSessionExpiryNotice == true`, 상세 상태 idle(실패 아님).
    - `상세 조회 429는 로그인을 유지하고 서버 메시지를 보여 준다`: `ApiException(statusCode: 429, message: ...)` → 상세 상태 failure, 오류 문구 == 서버 message, 인증 상태 변화 없음, 만료 안내 없음.
    - `상세 조회 네트워크 오류는 로그인을 유지한다`.
    - `상세 조회는 로그인이 풀린 뒤에는 요청하지 않는다`: 인증 false → 서비스 호출 0회, 예외 없음.
  - 기존 "상세 조회" 테스트는 가짜 서비스의 새 시그니처에 맞춘다.

- [ ] **Step 2: 실패 확인**
  Run: `flutter test --no-pub test/services/place_api_service_details_test.dart test/providers/map_provider_search_test.dart` (in `../tripbler`)
  Expected: 컴파일 실패(새 매개변수 없음) 또는 새 테스트 실패.

- [ ] **Step 3: 구현**
  - `fetchPlaceDetails`: 헤더 추가, 오류를 `ApiException`으로.
  - `loadSelectedPlaceDetails`: `_isSignedIn`이 아니면 바로 반환. `_authorizedRequest((header) => fetchPlaceDetails(placeId: ..., authorizationHeader: header))`. `SessionExpiredException` → `_raiseSessionExpiryNotice(error.sessionId)` 후 상세 상태를 idle로(현재 요청일 때만). `SessionChangedException` → 결과 반영 없이 로딩만 거둔다. 그 밖 → 기존처럼 failure, 문구는 `ApiException.message`(아니면 기존 기본 문구). 늦은 응답 무시(`_isCurrentDetails`)는 그대로.

- [ ] **Step 4: 통과 확인**
  Run: Step 2 명령 + `flutter test --no-pub test/` 전체, `dart format`·`flutter analyze --no-pub` 변경 파일.
  Expected: 모두 통과, 새 분석 문제 없음.

- [ ] **Step 5: 커밋(사용자 요청 시, 앱 저장소)**

---

### Task 2: 앱 — 진행 중인 같은 검색의 중복 제출 막기

**Files:**
- Modify: `../tripbler/lib/providers/map_provider.dart` (`_runSearch`)
- Modify: `../tripbler/test/providers/map_provider_search_test.dart`

**Interfaces:**
- Consumes: 기존 `_SearchRequest`(검색어 + `LatLng? center`, `==`가 둘 다 비교).
- Produces: 동작만. 진행 중 요청과 `==`인 요청은 새 요청 번호를 만들지 않고 반환한다.

- [ ] **Step 1: 실패하는 테스트 작성**
  - `같은 검색어와 같은 중심의 검색이 진행 중이면 다시 보내지 않는다`: 응답을 붙잡은 채 `search('경복궁', center: c)` 두 번 → 서비스 호출 1회.
  - `검색어가 다르면 진행 중이어도 보낸다`: `'경복궁'` 진행 중 `'남산타워'` → 호출 2회, 최신 결과만 반영(기존 규칙).
  - `중심이 다르면 진행 중이어도 보낸다`.
  - `진행 중이던 같은 검색이 끝나면 같은 검색을 다시 보낸다`: 첫 요청이 실패로 끝난 뒤 같은 검색과 `retrySearch()` 각각 호출 1회씩 더.
  - `검색 429는 로그인을 유지하고 서버 메시지를 보여 준다`: `ApiException(statusCode: 429, message: ...)` → `searchStatus == failure`, `searchError.message` == 서버 message, 만료 안내 없음.

- [ ] **Step 2: 실패 확인**
  Run: `flutter test --no-pub test/providers/map_provider_search_test.dart`
  Expected: 첫 테스트 실패(호출 2회).

- [ ] **Step 3: 구현** — 가장 최근에 시작해 아직 끝나지 않은 요청(`_SearchRequest?`)을 기억하고, 새 요청이 그것과 같으면 반환한다. 그 요청이 끝나면(성공·실패·만료 모두, `finally`) 비운다. 검색어 정규화는 기존 `SearchQueryNormalizer` 결과를 쓴다.

- [ ] **Step 4: 통과 확인**
  Run: `flutter test --no-pub test/`, `dart format`·`flutter analyze --no-pub` 변경 파일.
  Expected: 모두 통과.

- [ ] **Step 5: 커밋(사용자 요청 시, 앱 저장소)**

---

### Task 3: 백엔드 — 장소 버킷과 여러 창 동시 판정

**Files:**
- Modify: `src/main/java/com/tripbler/backend/common/ratelimit/RateLimitBucket.java`
- Modify: `src/main/java/com/tripbler/backend/common/ratelimit/AuthRateLimiter.java`
- Test: `src/test/java/com/tripbler/backend/common/ratelimit/AuthRateLimiterTest.java`

**Interfaces:**
- Produces:
  - `RateLimitBucket.PLACE_SEARCH_USER_MINUTE(10, 1분, 0)`, `PLACE_SEARCH_USER_HOUR(100, 60분, 0)`, `PLACE_DETAILS_USER_MINUTE(20, 1분, 0)`, `PLACE_DETAILS_USER_HOUR(200, 60분, 0)`.
  - `public void acquireAll(String key, List<RateLimitBucket> buckets)`: 같은 키로 여러 버킷을 기존 `tryAcquire`로 한 번에 판정(모두 허용일 때만 모두 기록, 거절 시 가장 긴 대기의 `TooManyRequestsException`).
  - 클래스 주석에 인증 전용이 아니라 범용 제한기임을 적는다(이름은 바꾸지 않음).

- [ ] **Step 1: 실패하는 테스트 작성** (기존 `MutableClock` 사용)
  - `장소_검색은_1분_10회까지_허용한다`: `acquireAll("u1", List.of(PLACE_SEARCH_USER_MINUTE, PLACE_SEARCH_USER_HOUR))` 10회 통과, 11번째 `Retry-After == 60`.
  - `장소_검색은_1시간_100회까지_허용한다`: 1분 간격 없이 넘지 않도록 6초씩 시계를 옮기며 100회 통과, 101번째의 `Retry-After`가 첫 요청 + 60분까지 남은 초.
  - `장소_상세는_1분_20회와_1시간_200회까지_허용한다`: 같은 방식.
  - `사용자와_종류별로_따로_센다`: `u1` 검색 소진 뒤 `u2` 검색 통과, `u1` 상세 통과.
  - `한_창이_거절하면_다른_창도_기록하지_않는다`: 1분 창 거절 요청 뒤 1시간 창 사용량이 늘지 않음(시계를 1분 옮긴 뒤 남은 시간 한도까지 통과로 확인).

- [ ] **Step 2: 실패 확인** — Run: `./gradlew test --tests "*AuthRateLimiterTest"` / Expected: 컴파일 실패.
- [ ] **Step 3: 구현** — 위 Interfaces 그대로.
- [ ] **Step 4: 통과 확인** — Run: Step 2 명령 / Expected: PASS.
- [ ] **Step 5: 커밋(사용자 요청 시)**

---

### Task 4: 백엔드 — 검색·상세에 사용자별 제한 적용

**Files:**
- Modify: `src/main/java/com/tripbler/backend/place/service/PlaceService.java`
- Modify: `src/main/java/com/tripbler/backend/place/controller/PlaceController.java` (`searchText`, `getPlaceDetails`)
- Create: `src/test/java/com/tripbler/backend/place/service/PlaceServiceTest.java`
- Modify: `src/test/java/com/tripbler/backend/place/controller/PlaceControllerTest.java`

**Interfaces:**
- Consumes: `AuthRateLimiter.acquireAll(String, List<RateLimitBucket>)`, 장소 버킷 4개.
- Produces:
  - `PlaceService(PlaceClient placeClient, AuthRateLimiter rateLimiter)`
  - `List<PlaceResponse> searchText(String userId, String query, Double latitude, Double longitude)`: 한도 확인 뒤 클라이언트 호출.
  - `PlaceResponse getPlaceDetails(String userId, String placeId)`: 같은 방식(상세 버킷).
  - 컨트롤러 두 메서드에 `@AuthenticationPrincipal Jwt jwt` 추가, `jwt.getSubject()` 전달. 검색은 기존 검증(검색어 정리, 좌표 쌍) 뒤에 서비스를 부른다.

- [ ] **Step 1: 실패하는 테스트 작성**
  - `PlaceServiceTest`(Mockito `PlaceClient`, 실제 `new AuthRateLimiter()`):
    - `검색_한도를_넘으면_429이고_클라이언트를_더_부르지_않는다`: 같은 사용자 10회 통과, 11번째 `TooManyRequestsException`, `verify(placeClient, times(10)).searchText(...)`.
    - `상세_한도를_넘으면_429이고_클라이언트를_더_부르지_않는다`: 20회 통과, 21번째 예외, `times(20)`.
    - `다른_사용자는_따로_센다`.
  - `PlaceControllerTest`: 검색·상세 서비스 호출 검증을 새 시그니처로(`jwt()` 기본 subject `"user"`). 상세 404 테스트에 `.with(jwt())`를 더해 유지. 추가:
    - `검색_429는_ErrorResponse와_Retry_After를_준다`: 서비스가 `TooManyRequestsException(42)` → 429, 헤더 `Retry-After: 42`, `$.code == "TOO_MANY_REQUESTS"`.
    - `검증에_실패한_검색은_한도를_쓰지_않는다`: 빈 검색어 400 → `verifyNoInteractions(placeService)`.

- [ ] **Step 2: 실패 확인** — Run: `./gradlew test --tests "*PlaceServiceTest" --tests "*PlaceControllerTest"` / Expected: 컴파일 실패.
- [ ] **Step 3: 구현** — 위 Interfaces 그대로.
- [ ] **Step 4: 통과 확인** — Run: `./gradlew test --tests "*Place*" --tests "*AuthRateLimit*"` / Expected: PASS.
- [ ] **Step 5: 커밋(사용자 요청 시)**

---

### Task 5: 백엔드 — 보안 규칙, 최근접 삭제, 문서

**Files:**
- Modify: `src/main/java/com/tripbler/backend/common/config/SecurityConfig.java` (장소 규칙, 현재 72~80행)
- Modify: `src/main/java/com/tripbler/backend/place/controller/PlaceController.java` (최근접 엔드포인트 삭제)
- Modify: `src/main/java/com/tripbler/backend/place/service/PlaceService.java` (`findNearestPlace` 삭제)
- Modify: `src/main/java/com/tripbler/backend/place/client/PlaceClient.java`, `GooglePlacesClient.java` (`findNearestPlace`, `searchNearbyWithoutType`, `createRequestWithoutType`, `calculateDistance` 삭제. 주변 조회 `searchNearby`·`createRequest`는 유지)
- Modify: `src/test/java/com/tripbler/backend/place/controller/PlaceControllerSecurityTest.java`, `PlaceControllerTest.java`, `src/test/java/com/tripbler/backend/place/client/GooglePlacesClientTest.java`
- Modify: `docs/places-api.md`, `README.md`(API 표 장소 행), `AGENTS.md`(아키텍처 표 장소 행)

**Interfaces:**
- Consumes: Task 4의 시그니처.
- Produces: 보안 규칙(등록 순서대로): `/api/v1/places/nearby`·`/api/v1/places/nearest` `denyAll()`(메서드 지정 없음, 주석: 다시 열면 로그인 + 사용자별 제한, 지운 테스트는 git 기록에서 복원) → `/api/v1/places/search`·`/api/v1/places/{placeId}` `authenticated()`. 기존 `/api/v1/places/**` `permitAll()` 삭제.

- [ ] **Step 1: 실패하는 테스트 작성** (`PlaceControllerSecurityTest`, 서비스 `@MockitoBean`)
  - `주변과_최근접은_토큰이_없으면_401이다`: `GET`·`HEAD` × `nearby`·`nearest` → 401, `verifyNoInteractions(placeService)`.
  - `주변과_최근접은_유효한_토큰이어도_403이다`: 같은 조합에 `jwt()` → 403, 서비스 호출 0회(`getPlaceDetails(any(), eq("nearest"))` 포함).
  - `상세는_토큰이_없으면_401이다`, `상세는_토큰이_있으면_200이다`(기존 `상세_조회는_토큰_없이_호출할_수_있다`를 대체).
  - `끝_슬래시_변형도_서비스를_호출하지_않는다`: `/nearby/`, `/nearest/`에 `jwt()` → 2xx가 아니고 서비스 호출 0회.
  - 기존 `주변_검색은_토큰_없이_호출할_수_있다`, `최근접_조회는_토큰_없이_호출할_수_있다`는 위 테스트로 대체해 지운다.
- [ ] **Step 2: 실패 확인** — Run: `./gradlew test --tests "*PlaceControllerSecurityTest"` / Expected: 401·403 기대 테스트 실패(지금은 200/204).
- [ ] **Step 3: 구현** — 보안 규칙 변경, 최근접 코드 삭제. 함께 지울 테스트: `PlaceControllerTest`의 주변·최근접 컨트롤러 테스트 전부, `GooglePlacesClientTest`의 최근접 테스트(`최근접*`). `GooglePlacesClientTest`의 주변 검색 테스트는 유지.
- [ ] **Step 4: 문서** — `docs/places-api.md`: 엔드포인트 표(상세 로그인 필수, 주변 차단, 최근접 삭제), "최근접 장소" 절 삭제, 주변 절에 차단 사실, 호출 제한 절(숫자는 초기 설정값, 429 + `Retry-After`, 비용 상한은 Google Cloud 쿼터·예산 알림), 기존 "사용자별 호출 제한과 캐시는 없다" 문장 교체. README·AGENTS.md의 장소 행을 같은 내용으로.
- [ ] **Step 5: 통과 확인** — Run: `./gradlew test`(DB 테스트는 `SPRING_DATASOURCE_PASSWORD` 없으면 컨텍스트 로드 실패만 허용) / Expected: DB 컨텍스트 실패 외 전부 통과. `git diff --check` 통과.
- [ ] **Step 6: 커밋(사용자 요청 시)**

---

### Task 6: 점검
- [ ] 백엔드: `code-inspector`(Sonnet)와 `security-reviewer`에 설계·계획 경로와 변경 파일 목록을 주고 점검. 앱: 앱 저장소 규칙대로 `code-inspector`.
- [ ] 보고를 사용자에게 전달. 수정은 사용자가 고른 항목만 `code-fixer`로, 끝나면 한 번에 재점검.
- [ ] 사용자에게 DB 비밀번호로 `./gradlew test` 전체 실행 요청.
- [ ] `D:\personal_project\progress.md` 갱신(실행한 검증, 미검증: 실제 Google 호출, 실기기 429 표시).
