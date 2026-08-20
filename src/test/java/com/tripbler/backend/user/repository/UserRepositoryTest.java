package com.tripbler.backend.user.repository;

import com.tripbler.backend.user.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    void saveAndFindByEmail() {
        // given
        String email = "test-" + UUID.randomUUID() + "@tripbler.com";
        User user = new User(email);

        // when
        User savedUser = userRepository.save(user);
        Optional<User> foundUser = userRepository.findByEmail(email);

        // then
        assertNotNull(savedUser.getId());
        assertTrue(foundUser.isPresent());
        assertEquals(email, foundUser.get().getEmail());
    }
}