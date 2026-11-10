package com.tripbler.backend.user.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.tripbler.backend.auth.entity.RefreshToken;
import com.tripbler.backend.auth.exception.InvalidRefreshTokenException;
import com.tripbler.backend.auth.repository.RefreshTokenRepository;
import com.tripbler.backend.auth.service.JwtTokenService;
import com.tripbler.backend.auth.service.RefreshTokenService;
import com.tripbler.backend.user.dto.TokenRefreshRequest;
import com.tripbler.backend.user.dto.UserLoginRequest;
import com.tripbler.backend.user.entity.User;
import com.tripbler.backend.user.entity.UserRole;
import com.tripbler.backend.user.repository.UserRepository;

/**
 * 로그아웃 계약 흐름 테스트.
 *
 * 실제 AuthService·RefreshTokenService에, 사용자당 한 행·토큰 값 고유라는 refresh_tokens 테이블 규칙을
 * 따르는 메모리 저장소를 붙여 "같은 계정으로 다시 로그인한 뒤 이전 로그아웃이 도착하는" 순서를 재현한다.
 * DB(PostgreSQL)는 쓰지 않으므로 deleteByToken의 JPQL은 여기서 검증되지 않는다(조건부 삭제 의미만 대역으로 재현).
 */
class AuthLogoutFlowTest {

    /** refresh_tokens 테이블 대역: 사용자당 한 행, 토큰 값 고유. */
    private final List<RefreshToken> rows = new ArrayList<>();

    private AuthService authService;
    private User user;

    @BeforeEach
    void setUp() {
        RefreshTokenRepository repository = mock(RefreshTokenRepository.class);

        when(repository.findByUser(any())).thenAnswer(invocation -> rows.stream()
            .filter(row -> row.getUser() == invocation.getArgument(0))
            .findFirst());

        when(repository.findByToken(anyString())).thenAnswer(invocation -> rows.stream()
            .filter(row -> row.getToken().equals(invocation.getArgument(0)))
            .findFirst());

        when(repository.save(any(RefreshToken.class))).thenAnswer(invocation -> {
            RefreshToken row = invocation.getArgument(0);

            if (!rows.contains(row)) {
                setId(row, (long) rows.size() + 1);
                rows.add(row);
            }

            return row;
        });

        // DELETE ... WHERE token = ? 와 같은 의미: 값이 일치하는 행만 지우고 지운 수를 돌려준다.
        when(repository.deleteByToken(anyString())).thenAnswer(invocation -> {
            String token = invocation.getArgument(0);
            int before = rows.size();
            rows.removeIf(row -> row.getToken().equals(token));
            return before - rows.size();
        });

        RefreshTokenService refreshTokenService =
            new RefreshTokenService(repository, 14L);

        user = mock(User.class);
        when(user.getId()).thenReturn(1L);
        when(user.getRole()).thenReturn(UserRole.USER);
        when(user.getPassword()).thenReturn("encoded-password");

        UserRepository userRepository = mock(UserRepository.class);
        when(userRepository.findByLoginId("traveler")).thenReturn(Optional.of(user));

        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);

        AtomicInteger accessTokenSequence = new AtomicInteger();
        JwtTokenService jwtTokenService = mock(JwtTokenService.class);
        when(jwtTokenService.createAccessToken(anyLong(), any()))
            .thenAnswer(invocation -> "access-" + accessTokenSequence.incrementAndGet());

        authService = new AuthService(
            userRepository,
            passwordEncoder,
            jwtTokenService,
            refreshTokenService
        );
    }

    private String login() {
        return authService
            .login(new UserLoginRequest("traveler", "password"))
            .refreshToken();
    }

    @Test
    void 같은_계정으로_재로그인한_뒤_이전_로그아웃이_도착해도_새_토큰으로_재발급된다() {
        // 1) 로그인한 기기가 로그아웃: 앱은 로컬 토큰을 먼저 지우고 서버 요청은 늦게 도착한다.
        String oldRefreshToken = login();

        // 2) 그 사이 같은 계정으로 다시 로그인해 같은 행의 토큰 값이 바뀐다.
        String newRefreshToken = login();
        assertNotEquals(oldRefreshToken, newRefreshToken);

        // 3) 늦게 도착한 이전 로그아웃은 이전 토큰만 가리키므로 아무것도 지우지 않는다.
        authService.logout(oldRefreshToken);

        // 4) 새 세션은 새 Refresh Token으로 계속 재발급된다.
        String accessToken = authService
            .refresh(new TokenRefreshRequest(newRefreshToken))
            .accessToken();

        assertTrue(accessToken.startsWith("access-"));
        assertEquals(1, rows.size());
        assertEquals(newRefreshToken, rows.get(0).getToken());

        // 이전 토큰으로는 재발급할 수 없다.
        assertThrows(
            InvalidRefreshTokenException.class,
            () -> authService.refresh(new TokenRefreshRequest(oldRefreshToken))
        );
    }

    @Test
    void 로그아웃하면_그_토큰은_폐기돼_재발급할_수_없다() {
        String refreshToken = login();

        authService.logout(refreshToken);

        assertTrue(rows.isEmpty());
        assertThrows(
            InvalidRefreshTokenException.class,
            () -> authService.refresh(new TokenRefreshRequest(refreshToken))
        );
    }

    @Test
    void 이미_폐기됐거나_모르는_토큰으로_로그아웃해도_오류_없이_끝나고_다른_토큰을_건드리지_않는다() {
        String refreshToken = login();
        authService.logout(refreshToken);

        // 같은 로그아웃이 다시 와도(재전송) 조용히 끝난다.
        authService.logout(refreshToken);
        authService.logout("unknown-token");
        assertTrue(rows.isEmpty());

        String nextRefreshToken = login();
        authService.logout("unknown-token");

        assertEquals(1, rows.size());
        assertEquals(nextRefreshToken, rows.get(0).getToken());
    }

    @Test
    void 만료된_토큰도_로그아웃으로_정리된다() throws Exception {
        String refreshToken = login();
        RefreshToken row = rows.get(0);
        setExpiresAt(row, LocalDateTime.now().minusDays(1));

        authService.logout(refreshToken);

        assertFalse(rows.contains(row));
    }

    private static void setId(RefreshToken row, Long id) throws ReflectiveOperationException {
        Field field = RefreshToken.class.getDeclaredField("id");
        field.setAccessible(true);
        field.set(row, id);
    }

    private static void setExpiresAt(RefreshToken row, LocalDateTime expiresAt)
        throws ReflectiveOperationException {
        Field field = RefreshToken.class.getDeclaredField("expiresAt");
        field.setAccessible(true);
        field.set(row, expiresAt);
    }
}
