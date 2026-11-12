package com.tripbler.backend.auth.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mail.MailSendException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.tripbler.backend.auth.repository.FindIdVerificationRepository;
import com.tripbler.backend.auth.repository.PasswordResetTokenRepository;
import com.tripbler.backend.auth.repository.PasswordResetVerificationRepository;
import com.tripbler.backend.common.mail.EmailService;
import com.tripbler.backend.common.ratelimit.AuthRateLimiter;
import com.tripbler.backend.user.entity.SocialAccount;
import com.tripbler.backend.user.entity.SocialProvider;
import com.tripbler.backend.user.entity.User;
import com.tripbler.backend.user.repository.SocialAccountRepository;
import com.tripbler.backend.user.repository.UserRepository;

/**
 * 실제 DB에서 메일 발송 실패 시 새 코드 발급이 롤백되고 이전 코드가 남는지 확인한다.
 * 요청 제한은 항상 허용하는 대역으로 둔다(제한 동작은 VerificationSendResponseParityTest에서 확인).
 * 실행에는 SPRING_DATASOURCE_PASSWORD 환경 변수가 필요하다.
 */
@SpringBootTest
class VerificationSendPersistenceTest {

    private static final String CLIENT_IP = "10.9.9.8";

    @Autowired private FindIdVerificationService findIdService;
    @Autowired private PasswordResetVerificationService passwordResetService;
    @Autowired private FindIdVerificationRepository findIdRepository;
    @Autowired private PasswordResetVerificationRepository passwordResetRepository;
    @Autowired private PasswordResetTokenRepository resetTokenRepository;
    @Autowired private SocialAccountRepository socialAccountRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PlatformTransactionManager transactionManager;

    @MockitoBean private EmailService emailService;
    @MockitoBean private AuthRateLimiter authRateLimiter;

    private final AtomicReference<String> lastCode = new AtomicReference<>();
    private User user;
    private SocialAccount socialAccount;
    private String email;

    @BeforeEach
    void setUp() {
        String unique = UUID.randomUUID().toString().substring(0, 8);
        email = "send-" + unique + "@gmail.com";
        user = userRepository.save(new User("send-" + unique, "발송테스트", "encoded-test-password"));
        socialAccount = socialAccountRepository.save(
            new SocialAccount(user, SocialProvider.GOOGLE, "pid-" + unique, email)
        );
        recordCodes();
    }

    private void recordCodes() {
        reset(emailService);
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
            resetTokenRepository.deleteAllByUserId(user.getId());
        });
        socialAccountRepository.delete(socialAccount);
        userRepository.delete(user);
    }

    @Test
    void findIdMailFailureKeepsPreviousCode() {
        findIdService.sendVerificationCode(email, CLIENT_IP);
        String previousCode = lastCode.get();

        doThrow(new MailSendException("smtp down"))
            .when(emailService).sendFindIdVerificationCode(anyString(), anyString());
        assertDoesNotThrow(() -> findIdService.sendVerificationCode(email, CLIENT_IP));

        assertEquals(
            1,
            findIdRepository.findAll().stream()
                .filter(v -> v.getEmail().equalsIgnoreCase(email))
                .count()
        );
        assertEquals(user.getLoginId(), findIdService.verifyCode(email, previousCode));
    }

    @Test
    void passwordResetMailFailureKeepsPreviousCode() {
        passwordResetService.sendVerificationCode(user.getLoginId(), email, CLIENT_IP);
        String previousCode = lastCode.get();

        doThrow(new MailSendException("smtp down"))
            .when(emailService).sendPasswordResetVerificationCode(anyString(), anyString());
        assertDoesNotThrow(
            () -> passwordResetService.sendVerificationCode(user.getLoginId(), email, CLIENT_IP)
        );

        assertEquals(
            1,
            passwordResetRepository.findAll().stream()
                .filter(v -> v.getEmail().equalsIgnoreCase(email))
                .count()
        );
        assertNotNull(passwordResetService.verifyCode(user.getLoginId(), email, previousCode));
    }
}
