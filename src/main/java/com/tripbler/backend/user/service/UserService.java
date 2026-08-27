package com.tripbler.backend.user.service;

import com.tripbler.backend.user.dto.LoginIdAvailabilityResponse;
import com.tripbler.backend.user.dto.UserCreateRequest;
import com.tripbler.backend.user.dto.UserPasswordChangeRequest;
import com.tripbler.backend.user.dto.UserResponse;
import com.tripbler.backend.user.entity.User;
import com.tripbler.backend.user.exception.CurrentPasswordMismatchException;
import com.tripbler.backend.user.exception.DuplicateLoginIdException;
import com.tripbler.backend.user.exception.UserNotFoundException;
import com.tripbler.backend.user.repository.UserRepository;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(
        UserRepository userRepository,
        PasswordEncoder passwordEncoder
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public UserResponse createUser(UserCreateRequest request) {

        if (userRepository.findByLoginId(request.loginId()).isPresent()) {
            throw new DuplicateLoginIdException();
        }

        String encodedPassword =
            passwordEncoder.encode(request.password());

        String nickname = request.nickname();

        if (nickname != null) {
            nickname = nickname.trim();

            if (nickname.isEmpty()) {
                nickname = null;
            }
        }

        User user = new User(
            request.loginId(),
            nickname,
            encodedPassword
        );

        User savedUser = userRepository.save(user);

        return UserResponse.from(savedUser);
    }

    @Transactional(readOnly = true)
    public LoginIdAvailabilityResponse checkLoginIdAvailability(
        String loginId
    ) {
        boolean available =
            userRepository.findByLoginId(loginId).isEmpty();

        return new LoginIdAvailabilityResponse(
            loginId,
            available
        );
    }

    @Transactional(readOnly = true)
    public UserResponse getUserById(Long userId) {

        User user = userRepository.findById(userId)
            .orElseThrow(UserNotFoundException::new);

        return UserResponse.from(user);
    }

    @Transactional
    public void changePassword(
        Long userId,
        UserPasswordChangeRequest request
    ) {
        User user = userRepository.findById(userId)
            .orElseThrow(UserNotFoundException::new);

        if (!passwordEncoder.matches(
            request.currentPassword(),
            user.getPassword()
        )) {
            throw new CurrentPasswordMismatchException();
        }

        String encodedNewPassword =
            passwordEncoder.encode(
                request.newPassword()
            );

        user.changePassword(
            encodedNewPassword
        );
    }
}