package com.tripbler.backend.user.repository;

import com.tripbler.backend.user.entity.User;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Transactional
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    void saveAndFindByLoginId() {

        // given
        String uniqueValue =
            UUID.randomUUID()
                .toString()
                .substring(0, 8);

        String loginId =
            "test-" + uniqueValue;

        String nickname =
            "테스트사용자";

        User user = new User(
            loginId,
            nickname,
            "encoded-test-password"
        );

        // when
        User savedUser =
            userRepository.save(user);

        Optional<User> foundUser =
            userRepository.findByLoginId(
                loginId
            );

        // then
        assertNotNull(
            savedUser.getId()
        );

        assertTrue(
            foundUser.isPresent()
        );

        assertEquals(
            loginId,
            foundUser.get().getLoginId()
        );
    }
}