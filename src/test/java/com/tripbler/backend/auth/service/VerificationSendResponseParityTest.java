package com.tripbler.backend.auth.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mail.MailSendException;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.jayway.jsonpath.JsonPath;
import com.tripbler.backend.auth.repository.FindIdVerificationRepository;
import com.tripbler.backend.auth.repository.PasswordResetVerificationRepository;
import com.tripbler.backend.common.mail.EmailService;
import com.tripbler.backend.user.entity.SocialAccount;
import com.tripbler.backend.user.entity.SocialProvider;
import com.tripbler.backend.user.entity.User;
import com.tripbler.backend.user.repository.SocialAccountRepository;
import com.tripbler.backend.user.repository.UserRepository;

/**
 * 가입된 이메일의 메일 발송 실패와 가입되지 않은 이메일 요청이
 * HTTP 상태, 본문, 이후 남은 한도까지 같은지 실제 요청 제한기로 확인한다(계정 존재 숨기기).
 * 실행에는 SPRING_DATASOURCE_PASSWORD 환경 변수가 필요하다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class VerificationSendResponseParityTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private FindIdVerificationRepository findIdRepository;
    @Autowired private PasswordResetVerificationRepository passwordResetRepository;
    @Autowired private SocialAccountRepository socialAccountRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PlatformTransactionManager transactionManager;

    @MockitoBean private EmailService emailService;

    private User user;
    private SocialAccount socialAccount;
    private String linkedEmail;
    private String unlinkedEmail;

    @BeforeEach
    void setUp() {
        String unique = UUID.randomUUID().toString().substring(0, 8);
        linkedEmail = "parity-" + unique + "@gmail.com";
        unlinkedEmail = "nobody-" + unique + "@gmail.com";
        user = userRepository.save(new User("parity-" + unique, "동일응답", "encoded-test-password"));
        socialAccount = socialAccountRepository.save(
            new SocialAccount(user, SocialProvider.GOOGLE, "pid-" + unique, linkedEmail)
        );
        doThrow(new MailSendException("smtp down"))
            .when(emailService).sendFindIdVerificationCode(anyString(), anyString());
        doThrow(new MailSendException("smtp down"))
            .when(emailService).sendPasswordResetVerificationCode(anyString(), anyString());
    }

    @AfterEach
    void tearDown() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            findIdRepository.deleteAllByEmailIgnoreCase(linkedEmail);
            passwordResetRepository.deleteAllByLoginIdIgnoreCase(user.getLoginId());
        });
        socialAccountRepository.delete(socialAccount);
        userRepository.delete(user);
    }

    private MockHttpServletResponse send(String path, String body, String ip) throws Exception {
        return mockMvc.perform(
            post(path)
                .with(request -> {
                    request.setRemoteAddr(ip);
                    return request;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
        ).andReturn().getResponse();
    }

    private void assertSameRateLimitedResponse(
        MockHttpServletResponse linked,
        MockHttpServletResponse unlinked
    ) throws Exception {
        assertEquals(429, linked.getStatus());
        assertEquals(429, unlinked.getStatus());
        assertEquals("60", linked.getHeader("Retry-After"));
        assertEquals("60", unlinked.getHeader("Retry-After"));

        String linkedBody = linked.getContentAsString();
        String unlinkedBody = unlinked.getContentAsString();
        assertEquals("TOO_MANY_REQUESTS", JsonPath.read(linkedBody, "$.code"));
        assertEquals(
            (String) JsonPath.read(linkedBody, "$.code"),
            JsonPath.read(unlinkedBody, "$.code")
        );
        assertEquals(
            (String) JsonPath.read(linkedBody, "$.message"),
            JsonPath.read(unlinkedBody, "$.message")
        );
    }

    @Test
    void findIdLinkedMailFailureAndUnlinkedEmailLookTheSame() throws Exception {
        String path = "/api/v1/auth/find-id/send-code";
        String ip = "ip-" + UUID.randomUUID();
        String otherIp = "ip-" + UUID.randomUUID();

        MockHttpServletResponse linked = send(path, "{\"email\": \"" + linkedEmail + "\"}", ip);
        MockHttpServletResponse unlinked = send(path, "{\"email\": \"" + unlinkedEmail + "\"}", otherIp);

        assertEquals(204, linked.getStatus());
        assertEquals(204, unlinked.getStatus());
        assertEquals(linked.getContentAsString(), unlinked.getContentAsString());

        assertSameRateLimitedResponse(
            send(path, "{\"email\": \"" + linkedEmail + "\"}", ip),
            send(path, "{\"email\": \"" + unlinkedEmail + "\"}", otherIp)
        );
    }

    @Test
    void passwordResetLinkedMailFailureAndUnlinkedEmailLookTheSame() throws Exception {
        String path = "/api/v1/auth/password-reset/send-code";
        String ip = "ip-" + UUID.randomUUID();
        String otherIp = "ip-" + UUID.randomUUID();
        String linkedBody =
            "{\"loginId\": \"" + user.getLoginId() + "\", \"email\": \"" + linkedEmail + "\"}";
        String unlinkedBody =
            "{\"loginId\": \"" + user.getLoginId() + "\", \"email\": \"" + unlinkedEmail + "\"}";

        MockHttpServletResponse linked = send(path, linkedBody, ip);
        MockHttpServletResponse unlinked = send(path, unlinkedBody, otherIp);

        assertEquals(204, linked.getStatus());
        assertEquals(204, unlinked.getStatus());
        assertEquals(linked.getContentAsString(), unlinked.getContentAsString());

        assertSameRateLimitedResponse(send(path, linkedBody, ip), send(path, unlinkedBody, otherIp));
    }
}
