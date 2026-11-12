package com.tripbler.backend.auth.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.tripbler.backend.auth.entity.FindIdVerification;
import com.tripbler.backend.auth.entity.PasswordResetVerification;
import com.tripbler.backend.auth.repository.FindIdVerificationRepository;
import com.tripbler.backend.auth.repository.PasswordResetTokenRepository;
import com.tripbler.backend.auth.repository.PasswordResetVerificationRepository;
import com.tripbler.backend.common.exception.BusinessException;
import com.tripbler.backend.common.exception.ErrorCode;
import com.tripbler.backend.common.mail.EmailService;
import com.tripbler.backend.common.ratelimit.AuthRateLimiter;
import com.tripbler.backend.user.entity.SocialAccount;
import com.tripbler.backend.user.entity.SocialProvider;
import com.tripbler.backend.user.entity.User;
import com.tripbler.backend.user.repository.SocialAccountRepository;
import com.tripbler.backend.user.repository.UserRepository;

/**
 * 실제 DB(PostgreSQL)에서 인증코드 오답 제한을 확인한다.
 * 테스트 클래스에 트랜잭션을 두지 않아 요청마다 실제로 커밋된다(롤백 문제를 잡기 위해).
 * 실행에는 SPRING_DATASOURCE_PASSWORD 환경 변수가 필요하다.
 */
@SpringBootTest
class VerificationAttemptPersistenceTest {

    private static final String WRONG_CODE = "wrong!";

    @Autowired private FindIdVerificationService findIdService;
    @Autowired private PasswordResetVerificationService passwordResetService;
    @Autowired private FindIdVerificationRepository findIdRepository;
    @Autowired private PasswordResetVerificationRepository passwordResetRepository;
    @Autowired private PasswordResetTokenRepository resetTokenRepository;
    @Autowired private SocialAccountRepository socialAccountRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private PlatformTransactionManager transactionManager;

    @MockitoBean private EmailService emailService;
    // 같은 이메일로 60초 안에 다시 발급하는 테스트가 있어 요청 제한은 항상 허용하는 대역으로 둔다.
    @MockitoBean private AuthRateLimiter authRateLimiter;

    private final AtomicReference<String> lastCode = new AtomicReference<>();
    private User user;
    private SocialAccount socialAccount;
    private String email;

    @BeforeEach
    void setUp() {
        String unique = UUID.randomUUID().toString().substring(0, 8);
        email = "verify-" + unique + "@gmail.com";
        user = userRepository.save(new User("verify-" + unique, "인증테스트", "encoded-test-password"));
        socialAccount = socialAccountRepository.save(
            new SocialAccount(user, SocialProvider.GOOGLE, "pid-" + unique, email)
        );

        doAnswer(invocation -> {
            lastCode.set(invocation.getArgument(1));
            return null;
        }).when(emailService).sendFindIdVerificationCode(anyString(), anyString());
        doAnswer(invocation -> {
            lastCode.set(invocation.getArgument(1));
            return null;
        }).when(emailService).sendPasswordResetVerificationCode(anyString(), anyString());
    }

    @AfterEach
    void tearDown() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            findIdRepository.deleteAllByEmailIgnoreCase(email);
            passwordResetRepository.deleteAllByLoginIdIgnoreCase(user.getLoginId());
            passwordResetRepository.deleteAllByLoginIdIgnoreCase("other-" + user.getLoginId());
            resetTokenRepository.deleteAllByUserId(user.getId());
        });
        socialAccountRepository.delete(socialAccount);
        userRepository.delete(user);
    }

    // 두 흐름(아이디 찾기, 비밀번호 재설정)을 같은 테스트로 확인하기 위한 묶음
    private interface Flow {
        String send();
        Object verify(String code);
        int attempts();
        boolean verified();
        Long latestId();
        String table();
        ErrorCode invalid();
        // 인증 레코드가 없는 입력으로 send-code와 verify-code를 부른다(가입·연동 안 됨 또는 loginId 불일치).
        void sendWithoutRecord();
        Object verifyWithoutRecord(String code);
    }

    private Flow flow(String name) {
        if (name.equals("findId")) {
            return new Flow() {
                public String send() {
                    findIdService.sendVerificationCode(email, "10.9.9.9");
                    return lastCode.get();
                }
                public Object verify(String code) { return findIdService.verifyCode(email, code); }
                public int attempts() { return latestFindId().getAttemptCount(); }
                public boolean verified() { return latestFindId().isVerified(); }
                public Long latestId() { return latestFindId().getId(); }
                public String table() { return "find_id_verifications"; }
                public ErrorCode invalid() { return ErrorCode.INVALID_FIND_ID_VERIFICATION_CODE; }
                public void sendWithoutRecord() {
                    findIdService.sendVerificationCode(unknownEmail(), "10.9.9.9");
                }
                public Object verifyWithoutRecord(String code) {
                    return findIdService.verifyCode(unknownEmail(), code);
                }
            };
        }
        return new Flow() {
            public String send() {
                passwordResetService.sendVerificationCode(user.getLoginId(), email, "10.9.9.9");
                return lastCode.get();
            }
            public Object verify(String code) {
                return passwordResetService.verifyCode(user.getLoginId(), email, code);
            }
            public int attempts() { return latestReset().getAttemptCount(); }
            public boolean verified() { return latestReset().isVerified(); }
            public Long latestId() { return latestReset().getId(); }
            public String table() { return "password_reset_verifications"; }
            public ErrorCode invalid() { return ErrorCode.INVALID_PASSWORD_RESET_VERIFICATION_CODE; }
            // 이메일은 연동돼 있지만 다른 loginId와 짝지은 경우(재설정 흐름에서 드러나면 안 되는 정보)
            public void sendWithoutRecord() {
                passwordResetService.sendVerificationCode("other-" + user.getLoginId(), email, "10.9.9.9");
            }
            public Object verifyWithoutRecord(String code) {
                return passwordResetService.verifyCode("other-" + user.getLoginId(), email, code);
            }
        };
    }

    private String unknownEmail() {
        return "none-" + email;
    }

    private FindIdVerification latestFindId() {
        return findIdRepository.findAll().stream()
            .filter(v -> v.getEmail().equalsIgnoreCase(email))
            .reduce((first, second) -> second.getId() > first.getId() ? second : first)
            .orElseThrow();
    }

    private PasswordResetVerification latestReset() {
        return passwordResetRepository.findAll().stream()
            .filter(v -> v.getEmail().equalsIgnoreCase(email)
                && v.getLoginId().equalsIgnoreCase(user.getLoginId()))
            .reduce((first, second) -> second.getId() > first.getId() ? second : first)
            .orElseThrow();
    }

    private ErrorCode errorOf(Flow flow, String code) {
        return assertThrows(BusinessException.class, () -> flow.verify(code)).getErrorCode();
    }

    // 외부 응답을 이루는 상태·code·message
    private static List<Object> responseOf(BusinessException exception) {
        ErrorCode errorCode = exception.getErrorCode();
        return List.of(errorCode.getStatus(), errorCode.getCode(), exception.getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = {"findId", "passwordReset"})
    void fourWrongThenCorrectSucceeds(String name) {
        Flow flow = flow(name);
        String code = flow.send();

        for (int i = 0; i < 4; i++) {
            assertEquals(flow.invalid(), errorOf(flow, WRONG_CODE));
        }

        assertNotNull(flow.verify(code));
        assertTrue(flow.verified());
    }

    @ParameterizedTest
    @ValueSource(strings = {"findId", "passwordReset"})
    void fifthWrongDiscardsCodeAndCorrectIsRejected(String name) {
        Flow flow = flow(name);
        String code = flow.send();

        for (int i = 0; i < 4; i++) {
            assertEquals(flow.invalid(), errorOf(flow, WRONG_CODE));
        }

        // 5번째 오답으로 폐기돼도 응답은 같다. 폐기 여부는 DB 상태와 정답 거절로 확인한다.
        assertEquals(flow.invalid(), errorOf(flow, WRONG_CODE));
        assertEquals(5, flow.attempts());
        assertFalse(flow.verified());

        assertEquals(flow.invalid(), errorOf(flow, code));
        assertEquals(5, flow.attempts());
        assertFalse(flow.verified());
    }

    @ParameterizedTest
    @ValueSource(strings = {"findId", "passwordReset"})
    void failedAttemptIsPersisted(String name) {
        Flow flow = flow(name);
        flow.send();

        errorOf(flow, WRONG_CODE);

        assertEquals(1, flow.attempts());
    }

    @ParameterizedTest
    @ValueSource(strings = {"findId", "passwordReset"})
    void usedOrDiscardedCodeDoesNotIncreaseAttempts(String name) {
        Flow flow = flow(name);

        String code = flow.send();
        flow.verify(code);
        assertEquals(flow.invalid(), errorOf(flow, WRONG_CODE));
        assertEquals(0, flow.attempts());

        flow.send();
        for (int i = 0; i < 5; i++) {
            errorOf(flow, WRONG_CODE);
        }
        assertEquals(flow.invalid(), errorOf(flow, WRONG_CODE));
        assertEquals(5, flow.attempts());
    }

    @ParameterizedTest
    @ValueSource(strings = {"findId", "passwordReset"})
    void reissuedCodeReplacesOldOne(String name) {
        Flow flow = flow(name);
        String oldCode = flow.send();
        String newCode = flow.send();

        if (!oldCode.equals(newCode)) {
            assertEquals(flow.invalid(), errorOf(flow, oldCode));
        }
        assertNotNull(flow.verify(newCode));
    }

    @ParameterizedTest
    @ValueSource(strings = {"findId", "passwordReset"})
    void expiredCodeDoesNotIncreaseAttempts(String name) {
        Flow flow = flow(name);
        flow.send();
        jdbcTemplate.update(
            "update " + flow.table() + " set expires_at = ? where id = ?",
            Timestamp.from(Instant.now().minus(1, ChronoUnit.MINUTES)),
            flow.latestId()
        );

        assertEquals(flow.invalid(), errorOf(flow, WRONG_CODE));
        assertEquals(0, flow.attempts());
    }

    @ParameterizedTest
    @ValueSource(strings = {"findId", "passwordReset"})
    void expiredCodeResponseIsSameAsInvalid(String name) {
        Flow flow = flow(name);
        flow.send();
        BusinessException wrong = assertThrows(BusinessException.class, () -> flow.verify(WRONG_CODE));

        String code = flow.send();
        jdbcTemplate.update(
            "update " + flow.table() + " set expires_at = ? where id = ?",
            Timestamp.from(Instant.now().minus(1, ChronoUnit.MINUTES)),
            flow.latestId()
        );
        BusinessException expired = assertThrows(BusinessException.class, () -> flow.verify(code));

        assertEquals(responseOf(wrong), responseOf(expired));
        assertFalse(flow.verified());
    }

    // H-1: 인증 레코드가 있든 없든 오답 응답이 매번 같아야 계정 존재(재설정은 아이디-이메일 연결)가 드러나지 않는다.
    @ParameterizedTest
    @ValueSource(strings = {"findId", "passwordReset"})
    void wrongAnswerResponsesDoNotRevealWhetherRecordExists(String name) {
        Flow flow = flow(name);
        flow.send();
        flow.sendWithoutRecord();

        for (int i = 0; i < 6; i++) {
            BusinessException withRecord =
                assertThrows(BusinessException.class, () -> flow.verify(WRONG_CODE));
            BusinessException withoutRecord =
                assertThrows(BusinessException.class, () -> flow.verifyWithoutRecord(WRONG_CODE));

            assertEquals(flow.invalid(), withRecord.getErrorCode(), "attempt " + (i + 1));
            assertEquals(responseOf(withoutRecord), responseOf(withRecord), "attempt " + (i + 1));
        }

        // 레코드가 있는 쪽은 실제로 폐기됐다(5회에서 멈춤).
        assertEquals(5, flow.attempts());
    }

    @Test
    void correctCodeWithMismatchedLoginIdIsRejectedWithoutAttempt() {
        String otherLoginId = "other-" + user.getLoginId();
        PasswordResetVerification saved = passwordResetRepository.save(
            new PasswordResetVerification(
                otherLoginId,
                email,
                passwordEncoder.encode("123456"),
                Instant.now().plus(5, ChronoUnit.MINUTES)
            )
        );

        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> passwordResetService.verifyCode(otherLoginId, email, "123456")
        );

        assertEquals(ErrorCode.INVALID_PASSWORD_RESET_VERIFICATION_CODE, exception.getErrorCode());
        assertEquals(0, passwordResetRepository.findById(saved.getId()).orElseThrow().getAttemptCount());
    }

    @ParameterizedTest
    @ValueSource(strings = {"findId", "passwordReset"})
    void concurrentWrongAnswersAreBothCounted(String name) throws Exception {
        Flow flow = flow(name);
        flow.send();

        List<Object> results = runConcurrently(
            () -> errorOf(flow, WRONG_CODE),
            () -> errorOf(flow, WRONG_CODE)
        );

        assertEquals(List.of(flow.invalid(), flow.invalid()), results);
        assertEquals(2, flow.attempts());
    }

    @ParameterizedTest
    @ValueSource(strings = {"findId", "passwordReset"})
    void fifthWrongAndCorrectRace(String name) throws Exception {
        Flow flow = flow(name);

        for (int round = 0; round < 20; round++) {
            String code = flow.send();
            for (int i = 0; i < 4; i++) {
                errorOf(flow, WRONG_CODE);
            }

            List<Object> results = runConcurrently(
                () -> errorOf(flow, WRONG_CODE),
                () -> {
                    try {
                        flow.verify(code);
                        return "SUCCESS";
                    } catch (BusinessException exception) {
                        return exception.getErrorCode();
                    }
                }
            );

            Object correctResult = results.get(1);
            if ("SUCCESS".equals(correctResult)) {
                assertTrue(flow.verified());
                assertEquals(4, flow.attempts());
            } else {
                // 응답 코드는 하나로 합쳐졌으므로 폐기 여부는 DB 상태로 판정한다.
                assertEquals(flow.invalid(), correctResult);
                assertEquals(5, flow.attempts());
                assertFalse(flow.verified());
            }
            assertTrue(flow.attempts() <= 5);
        }
    }

    private List<Object> runConcurrently(Callable<Object> first, Callable<Object> second)
        throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Object>> futures = new ArrayList<>();
            for (Callable<Object> task : List.of(first, second)) {
                futures.add(executor.submit(() -> {
                    start.await();
                    return task.call();
                }));
            }
            start.countDown();
            List<Object> results = new ArrayList<>();
            for (Future<Object> future : futures) {
                results.add(future.get(30, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            executor.shutdownNow();
        }
    }
}
