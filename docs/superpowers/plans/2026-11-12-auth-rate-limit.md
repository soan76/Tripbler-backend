# 공개 인증 API 남용 방지 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 인증코드 대입을 DB 기반 오답 제한으로 막고, 공개 인증 API·인증 메일 발송에 메모리 기반 요청 제한을 건다.

**Architecture:** 인증코드 검증은 `TransactionTemplate` 안에서 행을 잠근 채 결과를 커밋하고 바깥에서 예외로 바꾼다. 요청 제한은 `common/ratelimit`의 단일 `AuthRateLimiter`(한 개의 락, 버킷별 슬라이딩 윈도)가 맡고, 일반 경로는 `HandlerInterceptor`, `send-code`는 서비스가 IP·이메일을 함께 확보한다. 일시 차단은 `TooManyRequestsException` → 429 + `Retry-After`.

**Tech Stack:** Spring Boot 4.1, Java 17, Spring MVC, Spring Data JPA(PostgreSQL), JUnit 5, Mockito, MockMvc.

**Spec:** `docs/superpowers/specs/2026-11-12-auth-rate-limit-design.md`

## Global Constraints
- 새 라이브러리 추가 금지.
- 오류 응답은 기존 `ErrorResponse`(`timestamp`, `status`, `code`, `message`, `path`).
- 인증코드 오답 상한 5회, 폐기는 `attempt_count >= 5`. 테이블 구조 변경 없음.
- 메일: 정규화 이메일(`trim` + `toLowerCase(Locale.ROOT)`), 60초 간격, 최근 60분 5통, 두 흐름 합산.
- IP(`getRemoteAddr()`만): 로그인 1분 10, 메일 발송 10분 5(접수된 요청만), 복구·가입 1분 30, 세션 1분 60.
- 허용 시각과 같은 시각은 통과. `Retry-After`는 남은 초 올림, 최소 1.
- 버킷별 키 상한 10,000. 가득 차면 만료 키 정리 → 여전히 가득이면 새 키 429, `Retry-After`는 가장 먼저 만료되는 기존 키까지.
- 시간은 `Clock` 주입(`CryptoResponseCache` 패턴: `@Autowired` 공개 생성자는 `Clock.systemUTC()`, 패키지 생성자는 테스트용).
- 계정 존재 숨기기: 한도 안이면 가입 여부·발송 실패와 상관없이 `204`.
- 커밋은 사용자가 요청할 때만 한다. 각 Task의 커밋 단계는 "사용자 요청 시".
- 실제 DB 테스트는 `SPRING_DATASOURCE_PASSWORD` 환경 변수로 실행한다. 메일은 `@MockitoBean EmailService`로 대체한다.

## Review Focus
- 대소문자·공백만 다른 같은 이메일을 두 흐름에서 번갈아 보내면 한 키로 합산되어야 한다 → Task 2 `normalizesEmailAcrossFlows`.
- 만료된 코드에 오답을 넣어도 횟수는 오르지 않고 `EXPIRED_*`가 나와야 한다 → Task 1 `expiredCodeDoesNotIncreaseAttempts`.
- 비밀번호 재설정에서 코드는 맞지만 loginId가 연동 계정과 다르면 `INVALID_*`, 횟수 변화 없음(기존 동작 유지) → Task 1 `correctCodeWithMismatchedLoginIdIsRejectedWithoutAttempt`.
- CORS 사전 요청(`OPTIONS`)은 세지 않아야 한다 → Task 3 `optionsRequestIsNotCounted`.
- 남은 시간이 1초 미만이어도 `Retry-After`는 `1` 이상이어야 한다 → Task 2 `retryAfterRoundsUpSubSecond`.

---

### Task 1: 인증코드 오답 제한 (DB, 행 잠금)

**Files:**
- Modify: `src/main/java/com/tripbler/backend/auth/entity/FindIdVerification.java`, `PasswordResetVerification.java`
- Modify: `src/main/java/com/tripbler/backend/auth/repository/FindIdVerificationRepository.java`, `PasswordResetVerificationRepository.java`
- Modify: `src/main/java/com/tripbler/backend/auth/service/FindIdVerificationService.java`, `PasswordResetVerificationService.java`
- Modify: `src/main/java/com/tripbler/backend/common/exception/ErrorCode.java`
- Modify: `src/test/java/com/tripbler/backend/auth/service/FindIdVerificationServiceTest.java`, `PasswordResetVerificationServiceTest.java`
- Create: `src/test/java/com/tripbler/backend/auth/service/VerificationAttemptPersistenceTest.java`

**Interfaces:**
- Produces:
  - 두 엔티티: `public static final int MAX_FAILED_ATTEMPTS = 5;`, `boolean isAttemptsExhausted()`(`attemptCount >= MAX_FAILED_ATTEMPTS`), `void recordFailedAttempt()`(기존 `increaseAttemptCount` 대체).
  - `FindIdVerificationRepository`: `@Lock(LockModeType.PESSIMISTIC_WRITE) Optional<FindIdVerification> findFirstByEmailIgnoreCaseOrderByIdDesc(String email)`.
  - `PasswordResetVerificationRepository`: `@Lock(LockModeType.PESSIMISTIC_WRITE) Optional<PasswordResetVerification> findFirstByLoginIdIgnoreCaseAndEmailIgnoreCaseOrderByIdDesc(String loginId, String email)`.
  - `ErrorCode.FIND_ID_VERIFICATION_ATTEMPTS_EXCEEDED`, `ErrorCode.PASSWORD_RESET_VERIFICATION_ATTEMPTS_EXCEEDED`: 400, 메시지 `"인증코드를 여러 번 잘못 입력했습니다. 새 인증코드를 요청해 주세요."`.
  - 두 서비스 생성자에 `PlatformTransactionManager transactionManager` 추가(내부에서 `new TransactionTemplate(transactionManager)`). 클래스 단위 `@Transactional` 제거. `verifyCode`의 공개 시그니처는 그대로.

- [ ] **Step 1: 실패하는 DB 테스트 작성** — `VerificationAttemptPersistenceTest`
  - `@SpringBootTest`, 클래스에 `@Transactional` **없음**(요청마다 실제 커밋). `@MockitoBean EmailService`로 발송 코드를 `ArgumentCaptor`로 잡는다. `@AfterEach`에서 만든 사용자·연동 계정·인증 행을 지운다.
  - 준비 도우미: `User` + `SocialAccount(user, GOOGLE, "pid-"+uuid, "verify-"+uuid+"@gmail.com")` 저장 후 `sendVerificationCode`로 코드 발급. (Task 4 이전에는 기존 시그니처를 쓰고, Task 4에서 `clientIp` 인자를 추가한다.)
  - 두 흐름 각각(아이디 찾기·비밀번호 재설정) 다음 테스트:
    - `fourWrongThenCorrectSucceeds`: 오답 4회 각각 `INVALID_*`, 정답은 성공(loginId / reset token 반환).
    - `fifthWrongDiscardsCodeAndCorrectIsRejected`: 1~4번째 오답 `INVALID_*`, **5번째 오답 응답이 `*_ATTEMPTS_EXCEEDED`**, 이어서 정답도 `*_ATTEMPTS_EXCEEDED`.
    - `failedAttemptIsPersisted`: 오답 1회 후 `repository.findById(id)`의 `attemptCount == 1`.
    - `usedOrDiscardedCodeDoesNotIncreaseAttempts`: 성공 뒤 오답 → `INVALID_*`, 횟수 그대로. 폐기 뒤 오답 → 횟수 5 유지.
    - `reissuedCodeReplacesOldOne`: 코드 A 발급 → 코드 B 발급 → A 입력은 거절, B는 성공.
    - `expiredCodeDoesNotIncreaseAttempts`: `expiresAt`을 과거로 바꾼 행(JDBC 또는 리포지토리로 갱신)에서 오답 → `EXPIRED_*`, 횟수 0.
    - (재설정만) `correctCodeWithMismatchedLoginIdIsRejectedWithoutAttempt`: 같은 이메일의 인증 행을 다른 loginId로 직접 저장해 두고 정답 입력 → `INVALID_PASSWORD_RESET_VERIFICATION_CODE`, 횟수 0.
  - 동시성(두 흐름 각각, `ExecutorService` 2스레드 + `CountDownLatch`로 동시 시작):
    - `concurrentWrongAnswersAreBothCounted`: 오답 2개 동시 → `attemptCount == 2`.
    - `fifthWrongAndCorrectRace`: 오답 4회 후, 오답 1개와 정답 1개 동시. 결과 검증: 정답이 성공했으면 `verified == true` 이고 `attemptCount == 4`, 정답이 거절됐으면 그 오류는 `*_ATTEMPTS_EXCEEDED` 이고 `attemptCount == 5`. 어느 쪽이든 `attemptCount <= 5`. 20회 반복.

- [ ] **Step 2: 실패 확인**
  Run: `./gradlew test --tests "*VerificationAttemptPersistenceTest"` (환경 변수 `SPRING_DATASOURCE_PASSWORD` 설정)
  Expected: 컴파일 실패(`*_ATTEMPTS_EXCEEDED` 없음) 또는 `failedAttemptIsPersisted` 실패(횟수 0).

- [ ] **Step 3: 구현**
  - 엔티티 메서드, 잠금 조회 메서드, `ErrorCode` 2개 추가. 기존 `findTop...OrderByIdDesc`는 다른 곳에서 안 쓰이면 제거.
  - `verifyCode`: `transactionTemplate.execute(...)` 안에서 잠금 조회 → 순서대로 판정하고 **예외 대신 결과를 반환**, 바깥에서 결과가 오류면 `throw new BusinessException(errorCode)`.
    판정 순서: 없음 → `INVALID_*` / `isVerified()` → `INVALID_*` / `isExpired()` → `EXPIRED_*` / `isAttemptsExhausted()` → `*_ATTEMPTS_EXCEEDED` / 코드 불일치 → `recordFailedAttempt()` 후 `isAttemptsExhausted()`면 `*_ATTEMPTS_EXCEEDED` 아니면 `INVALID_*` / 계정 확인(기존 로직) 실패 → `INVALID_*`(횟수 변화 없음) / 성공 → `markVerified()`(재설정은 `resetTokenService.createResetToken(userId)`까지 트랜잭션 안).
  - 결과 타입은 각 서비스 안의 `private record VerifyOutcome(String value, ErrorCode error)`.
  - `sendVerificationCode`는 이 Task에서 `transactionTemplate.executeWithoutResult`로 기존 본문을 감싸기만 한다.

- [ ] **Step 4: Mockito 테스트 갱신**
  `FindIdVerificationServiceTest`, `PasswordResetVerificationServiceTest`: 생성자에 `mock(PlatformTransactionManager.class)` 전달, 조회 스텁을 새 잠금 메서드로, `verifyCodeFailsWhenCodeIsIncorrect`는 `recordFailedAttempt` 결과(`getAttemptCount() == 1`) 확인, 5번째 오답 `*_ATTEMPTS_EXCEEDED` 단위 테스트 1건씩 추가.

- [ ] **Step 5: 통과 확인**
  Run: `./gradlew test --tests "*VerificationAttemptPersistenceTest" --tests "*FindIdVerificationServiceTest" --tests "*PasswordResetVerificationServiceTest" --tests "*AuthControllerTest"`
  Expected: 모두 PASS.

- [ ] **Step 6: 커밋(사용자 요청 시)**

---

### Task 2: 요청 제한 핵심 (`AuthRateLimiter`)

**Files:**
- Create: `src/main/java/com/tripbler/backend/common/ratelimit/RateLimitBucket.java`
- Create: `src/main/java/com/tripbler/backend/common/ratelimit/AuthRateLimiter.java`
- Create: `src/main/java/com/tripbler/backend/common/exception/TooManyRequestsException.java`
- Modify: `src/main/java/com/tripbler/backend/common/exception/ErrorCode.java`, `GlobalExceptionHandler.java`
- Test: `src/test/java/com/tripbler/backend/common/ratelimit/AuthRateLimiterTest.java`

**Interfaces:**
- Produces:
  - `enum RateLimitBucket { LOGIN_IP(10, 1분, 0), SEND_CODE_IP(5, 10분, 0), SEND_CODE_EMAIL(5, 60분, 60초), RECOVERY_SIGNUP_IP(30, 1분, 0), SESSION_IP(60, 1분, 0) }` — 필드 `int maxRequests`, `Duration window`, `Duration minInterval`.
  - `@Component class AuthRateLimiter`: `@Autowired public AuthRateLimiter()` → `this(Clock.systemUTC(), 10_000)`, `AuthRateLimiter(Clock clock, int capacityPerBucket)`(패키지).
    - `public void acquire(RateLimitBucket bucket, String key)` — 거절 시 `TooManyRequestsException`.
    - `public void acquireSendCode(String clientIp, String email)` — `SEND_CODE_IP`·`SEND_CODE_EMAIL`(정규화)을 한 번에 판정, 둘 다 허용일 때만 둘 다 기록.
    - `public static String normalizeEmail(String email)` — `trim().toLowerCase(Locale.ROOT)`.
  - `class TooManyRequestsException extends BusinessException` — 생성자 `(long retryAfterSeconds)`, `ErrorCode.TOO_MANY_REQUESTS`, `long getRetryAfterSeconds()`.
  - `ErrorCode.TOO_MANY_REQUESTS`: 429, 코드 `"TOO_MANY_REQUESTS"`, 메시지 `"요청이 너무 많습니다. 잠시 후 다시 시도해 주세요."`.
  - `GlobalExceptionHandler`: `@ExceptionHandler(TooManyRequestsException.class)` → 429, 헤더 `Retry-After: <초>`, 본문 `ErrorResponse`.

- [ ] **Step 1: 실패하는 테스트 작성** — `AuthRateLimiterTest`(조작 가능한 `Clock`, 작은 용량 생성자)
  - `allowsUpToMaxThenRejectsWithRetryAfter`: `LOGIN_IP` 10회 통과, 11번째 `TooManyRequestsException`, `getRetryAfterSeconds() == 60`.
  - `passesExactlyAtAllowedTime`: 11번째 거절 뒤 첫 요청 시각 + 60초 정각에 통과.
  - `emailMinIntervalBoundary`: `acquireSendCode` 1회 → 59초 후 거절(`Retry-After == 1`) → 60초 정각 통과.
  - `emailHourlyLimitBoundary`: 60초 간격으로 5회 → 6번째 거절, `Retry-After`는 첫 발송 + 60분까지 남은 초 → 그 정각 통과.
  - `normalizesEmailAcrossFlows`: `" User@Gmail.com "` 후 61초 뒤 `"user@gmail.com"` 등 섞어 5회 → 6번째 거절.
  - `sendCodeReturnsLongerWaitWhenBothLimited`: IP 한도와 이메일 한도가 모두 찬 상태 → `Retry-After`가 둘 중 큰 값.
  - `rejectionByOneLimitDoesNotConsumeTheOther`: 이메일 한도로 거절된 요청 뒤 같은 IP의 다른 이메일 요청 횟수가 줄지 않음(IP 남은 횟수로 확인), 반대도.
  - `bucketsAreIndependent`: `LOGIN_IP` 소진 뒤 같은 IP의 `SESSION_IP`는 통과.
  - `retryAfterRoundsUpSubSecond`: 남은 시간 0.2초 → `1`.
  - `fullCapacityRejectsNewKeyUntilEarliestExpires`: 용량 2, 키 A(t0)·B(t0+10s) → 새 키 C 거절, `Retry-After == 50`(A가 t0+60s에 만료) → t0+60s에 C 통과, A·B 기존 제한은 유지.
  - `concurrentSendCodeReservesOnce`: 같은 이메일 `acquireSendCode` 2스레드 동시 → 정확히 1건 성공.

- [ ] **Step 2: 실패 확인**
  Run: `./gradlew test --tests "*AuthRateLimiterTest"` — Expected: 컴파일 실패.

- [ ] **Step 3: 구현**
  - 모든 판정·기록은 하나의 `synchronized` 메서드 `tryAcquire(List<Key>)`에서 한다. 버킷별 `Map<String, ArrayDeque<Instant>>`.
  - 키별 판정(now 기준): `t > now - window`인 기록만 남김 → 개수 `>= max`면 대기 = `가장 오래된 기록 + window - now`; `minInterval > 0`이고 마지막 기록이 있으면 대기 = `max(대기, 마지막 + minInterval - now)`. 대기 `<= 0`이면 허용.
  - 키 보존 기간 = `max(window, minInterval)`. 새 키이고 버킷이 용량에 도달하면 `마지막 기록 <= now - 보존 기간`인 키를 정리하고, 그래도 가득이면 대기 = `min(각 키의 마지막 기록 + 보존 기간) - now`.
  - 모든 키가 허용일 때만 각 키에 `now` 추가. 아니면 아무것도 기록하지 않고 `ceil(최대 대기 ms / 1000)`(최소 1)로 예외.
  - 새 키 거절 시 `log.warn`.

- [ ] **Step 4: 통과 확인**
  Run: `./gradlew test --tests "*AuthRateLimiterTest"` — Expected: PASS.

- [ ] **Step 5: 커밋(사용자 요청 시)**

---

### Task 3: IP 인터셉터와 경로 연결

**Files:**
- Create: `src/main/java/com/tripbler/backend/common/ratelimit/AuthRateLimitInterceptor.java`
- Modify: `src/main/java/com/tripbler/backend/common/config/WebConfig.java`
- Test: `src/test/java/com/tripbler/backend/common/ratelimit/AuthRateLimitInterceptorTest.java`

**Interfaces:**
- Consumes: `AuthRateLimiter.acquire(RateLimitBucket, String)`, `TooManyRequestsException`.
- Produces: `AuthRateLimitInterceptor implements HandlerInterceptor` — 생성자 `(AuthRateLimiter limiter)`, **`@Component` 아님**.
  - `WebConfig`는 생성자에 `ObjectProvider<AuthRateLimiter>`를 받아 `addInterceptors`에서 빈이 있으면 `new AuthRateLimitInterceptor(limiter)`를 등록한다(기존 `@WebMvcTest` 14개가 limiter 빈 없이도 뜨도록).
  - 경로 매핑(메서드 + 경로 정확 일치):
    - `POST /api/v1/auth/login` → `LOGIN_IP`
    - `POST /api/v1/auth/refresh`, `POST /api/v1/auth/logout` → `SESSION_IP`
    - `POST /api/v1/auth/find-id/verify-code`, `POST /api/v1/auth/password-reset/verify-code`, `POST /api/v1/auth/password-reset`, `POST /api/v1/users`, `GET /api/v1/users/check-login-id` → `RECOVERY_SIGNUP_IP`
    - 그 밖(두 `send-code` 포함)과 `OPTIONS`는 통과.

- [ ] **Step 1: 실패하는 테스트 작성** — `@WebMvcTest({AuthController.class, UserController.class})`, 기존 `AuthControllerTest`의 `@Import`(보안 설정) + `AuthRateLimiter` 빈(고정 `Clock`)을 `@TestConfiguration`으로 제공, 서비스는 `@MockitoBean`.
  - `loginIsLimitedPerIp`: 로그인 10회 통과, 11번째 429, 헤더 `Retry-After: 60`, 본문 `$.status == 429`, `$.code == "TOO_MANY_REQUESTS"`, `$.path == "/api/v1/auth/login"`, `$.timestamp` 존재.
  - `refreshIsCountedSeparatelyFromLogin`: 로그인 소진 뒤 `refresh` 통과.
  - `checkLoginIdIsInRecoverySignupBucket`: `check-login-id` 30회 통과, 31번째 429.
  - `differentIpIsNotAffected`: `with(request -> { request.setRemoteAddr("10.0.0.2"); return request; })` 요청은 통과.
  - `optionsRequestIsNotCounted`: `OPTIONS /api/v1/auth/login`(CORS 사전 요청 헤더 포함) 20회 뒤에도 로그인 통과.

- [ ] **Step 2: 실패 확인**
  Run: `./gradlew test --tests "*AuthRateLimitInterceptorTest"` — Expected: FAIL(11번째도 200).

- [ ] **Step 3: 구현** — `preHandle`에서 매핑된 버킷이 있으면 `limiter.acquire(bucket, request.getRemoteAddr())`, 예외는 그대로 던져 `GlobalExceptionHandler`가 처리한다.

- [ ] **Step 4: 통과 + 회귀 확인**
  Run: `./gradlew test --tests "*AuthRateLimitInterceptorTest" --tests "*ControllerTest"` — Expected: 모두 PASS.

- [ ] **Step 5: 커밋(사용자 요청 시)**

---

### Task 4: 인증 메일 발송 제한과 발송 실패 처리

**Files:**
- Modify: `src/main/java/com/tripbler/backend/auth/service/FindIdVerificationService.java`, `PasswordResetVerificationService.java`
- Modify: `src/main/java/com/tripbler/backend/user/controller/AuthController.java`
- Modify: `src/test/java/com/tripbler/backend/auth/service/FindIdVerificationServiceTest.java`, `PasswordResetVerificationServiceTest.java`, `VerificationAttemptPersistenceTest.java`
- Modify: `src/test/java/com/tripbler/backend/user/controller/AuthControllerTest.java`
- Create: `src/test/java/com/tripbler/backend/auth/service/VerificationSendPersistenceTest.java`, `VerificationSendResponseParityTest.java`
- Create: `docs/auth-rate-limit-api.md`; Modify: `README.md`(API 개요에 429 한 줄), `AGENTS.md`(추가 API 설명 목록에 새 문서)

**Interfaces:**
- Consumes: `AuthRateLimiter.acquireSendCode(String clientIp, String email)`.
- Produces:
  - `FindIdVerificationService.sendVerificationCode(String email, String clientIp)`
  - `PasswordResetVerificationService.sendVerificationCode(String loginId, String email, String clientIp)`
  - 두 서비스 생성자에 `AuthRateLimiter` 추가.
  - `AuthController`의 두 `send-code` 메서드에 `HttpServletRequest request` 인자 추가, `request.getRemoteAddr()` 전달. 응답은 기존 `204`.

- [ ] **Step 1: 실패하는 테스트 작성**
  - Mockito(두 서비스): `sendAcquiresLimitBeforeAccountLookup`(`InOrder`: `acquireSendCode` → `socialAccountRepository` 조회), `unlinkedEmailStillAcquiresLimit`, `rateLimitedRequestDoesNotLookUpAccount`(limiter가 `TooManyRequestsException` → 조회·발송 없음, 예외 전파), `mailFailureIsSwallowed`(`emailService`가 `MailSendException` → 예외 없이 끝남).
  - `VerificationSendPersistenceTest`(`@SpringBootTest`, 비트랜잭션, `@MockitoBean EmailService`, `@MockitoBean AuthRateLimiter`(아무것도 안 함 = 항상 허용)):
    - `mailFailureKeepsPreviousCode`: 코드 A 발급 → 두 번째 발송에서 `EmailService`가 `MailSendException` → 예외 없음 → 코드 A로 검증 성공, 해당 이메일의 인증 행은 1개(A). 두 흐름 각각.
  - `VerificationSendResponseParityTest`(`@SpringBootTest` + `@AutoConfigureMockMvc`, 실제 `AuthRateLimiter`, `@MockitoBean EmailService`, 요청마다 고유 `remoteAddr`·이메일):
    - `linkedMailFailureAndUnlinkedEmailLookTheSame`: (a) 가입 이메일 + 발송 실패, (b) 미가입 이메일 → 둘 다 `204`·빈 본문. 각 이메일로 즉시 재요청 → 둘 다 `429`, `Retry-After: 60`, 같은 `code`·`message`. 두 흐름 각각.
  - `AuthControllerTest`: `sendVerificationCode` 검증을 새 시그니처(`"127.0.0.1"`)로 갱신, `send-code`가 `TooManyRequestsException(42)`를 받으면 429·`Retry-After: 42`.
  - `VerificationAttemptPersistenceTest`: 발급 도우미를 새 시그니처로 갱신하고 `@MockitoBean AuthRateLimiter`(항상 허용)를 추가한다(`reissuedCodeReplacesOldOne`이 60초 안에 재발송하기 때문).

- [ ] **Step 2: 실패 확인**
  Run: `./gradlew test --tests "*VerificationServiceTest" --tests "*VerificationSend*Test" --tests "*AuthControllerTest"` — Expected: 컴파일 실패.

- [ ] **Step 3: 구현**
  - `sendVerificationCode`: ① `authRateLimiter.acquireSendCode(clientIp, email)` ② `try { transactionTemplate.executeWithoutResult(status -> 기존 발급·발송 본문); } catch (MailException e) { log.warn("인증 메일 발송 실패", e); }` — 발송 예외가 템플릿 안에서 나가므로 삭제·저장은 롤백되고, 한도 차감은 그대로다. 로그에 이메일 원문을 남기지 않는다.

- [ ] **Step 4: 문서**
  `docs/auth-rate-limit-api.md`: 묶음별 경로·한도 표, 429 응답 예시(`Retry-After` 포함), 인증코드 5회 폐기 오류 코드 2개, 메일 발송 실패·미가입 이메일도 `204`, `X-Forwarded-For` 미사용과 단일 서버 전제. README API 개요에 한 줄, AGENTS.md "추가 API 설명"에 문서 경로 추가.

- [ ] **Step 5: 통과 확인**
  Run: `./gradlew test` (환경 변수 `SPRING_DATASOURCE_PASSWORD` 설정) — Expected: 전체 PASS.

- [ ] **Step 6: 커밋(사용자 요청 시)**

---

### Task 5: 점검

- [ ] `code-inspector`(Sonnet)와 `security-reviewer`에 설계 문서 경로와 변경 파일 목록을 주고 점검을 맡긴다.
- [ ] 보고 내용을 사용자에게 전달한다. 수정은 사용자가 요청한 항목만 `code-fixer`로 진행하고, 모두 끝나면 한 번에 재점검한다.
- [ ] `D:\personal_project\progress.md` 갱신(실행한 테스트와 결과, 미검증 항목: 실제 메일 서버 장애, 앱 화면 표시).
