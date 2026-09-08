package com.tripbler.backend.user.repository;

import com.tripbler.backend.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByLoginId(String loginId);

    boolean existsByNickname(String nickname);

    boolean existsByNicknameAndIdNot(
        String nickname,
        Long id
    );
}