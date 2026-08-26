package com.tripbler.backend.user.service;

import com.tripbler.backend.user.dto.UserCreateRequest;
import com.tripbler.backend.user.dto.UserResponse;
import com.tripbler.backend.user.entity.User;
import com.tripbler.backend.user.exception.DuplicateEmailException;
import com.tripbler.backend.user.exception.UserNotFoundException;
import com.tripbler.backend.user.repository.UserRepository;
import com.tripbler.backend.user.dto.UserUpdateRequest;
import com.tripbler.backend.user.dto.UserPasswordChangeRequest;
import com.tripbler.backend.user.exception.CurrentPasswordMismatchException;
import com.tripbler.backend.user.exception.DuplicateLoginIdException;
import com.tripbler.backend.user.dto.LoginIdAvailabilityResponse;

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

        if (userRepository.findByEmail(request.email()).isPresent()) {
            throw new DuplicateEmailException();
        }

        String encodedPassword =
            passwordEncoder.encode(request.password());

        User user = new User(
            request.loginId(),
            request.nickname(),
            request.email(),
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
    public UserResponse updateUser(
        Long userId,
        UserUpdateRequest request
    ) {
        User user = userRepository.findById(userId)
            .orElseThrow(UserNotFoundException::new);

        if (user.getEmail().equals(request.email())) {
            return UserResponse.from(user);
        }

        userRepository.findByEmail(request.email())
            .ifPresent(existingUser -> {
                if (!existingUser.getId().equals(userId)) {
                    throw new DuplicateEmailException();
                }
            });

        user.changeEmail(request.email());

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