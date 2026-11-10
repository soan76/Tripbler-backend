package com.tripbler.backend.auth.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.tripbler.backend.auth.entity.RefreshToken;
import com.tripbler.backend.auth.exception.InvalidRefreshTokenException;
import com.tripbler.backend.auth.service.RefreshTokenService;
import com.tripbler.backend.user.entity.User;
import com.tripbler.backend.user.repository.UserRepository;

/**
 * 실제 DB(PostgreSQL)에서 로그아웃 폐기 쿼리(deleteByToken)를 실행해 확인한다.
 * 각 테스트는 트랜잭션 안에서 실행되고 끝나면 되돌린다.
 */
@SpringBootTest
@Transactional
class RefreshTokenRepositoryTest {

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Autowired
    private UserRepository userRepository;

    private User saveUser() {
        String unique = UUID.randomUUID().toString().substring(0, 8);

        return userRepository.save(
            new User("logout-" + unique, "로그아웃테스트", "encoded-test-password")
        );
    }

    @Test
    void 일치하는_토큰만_한_건_지우고_일치하지_않으면_아무것도_지우지_않는다() {
        User user = saveUser();
        refreshTokenRepository.saveAndFlush(
            new RefreshToken(user, "token-" + UUID.randomUUID(), LocalDateTime.now().plusDays(14))
        );
        String storedToken = refreshTokenRepository.findByUser(user).orElseThrow().getToken();

        assertEquals(0, refreshTokenRepository.deleteByToken("not-" + storedToken));
        assertTrue(refreshTokenRepository.findByToken(storedToken).isPresent());

        assertEquals(1, refreshTokenRepository.deleteByToken(storedToken));
        assertFalse(refreshTokenRepository.findByToken(storedToken).isPresent());
    }

    @Test
    void 같은_계정으로_재로그인한_뒤_이전_토큰으로_로그아웃해도_새_토큰은_남아_재발급된다() {
        User user = saveUser();

        String oldToken = refreshTokenService.createOrUpdate(user);
        String newToken = refreshTokenService.createOrUpdate(user);
        refreshTokenRepository.flush();
        assertNotEquals(oldToken, newToken);

        // 늦게 도착한 이전 로그아웃
        assertFalse(refreshTokenService.revoke(oldToken));

        RefreshToken validated = refreshTokenService.validate(newToken);
        assertEquals(user.getId(), validated.getUser().getId());

        assertThrows(
            InvalidRefreshTokenException.class,
            () -> refreshTokenService.validate(oldToken)
        );
    }

    @Test
    void 한_사용자의_로그아웃은_다른_사용자의_토큰을_지우지_않는다() {
        User first = saveUser();
        User second = saveUser();

        String firstToken = refreshTokenService.createOrUpdate(first);
        String secondToken = refreshTokenService.createOrUpdate(second);
        refreshTokenRepository.flush();

        assertTrue(refreshTokenService.revoke(firstToken));

        assertFalse(refreshTokenRepository.findByToken(firstToken).isPresent());
        assertTrue(refreshTokenRepository.findByToken(secondToken).isPresent());
    }
}
