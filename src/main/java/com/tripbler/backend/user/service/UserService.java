package com.tripbler.backend.user.service;

import com.tripbler.backend.user.dto.LoginIdAvailabilityResponse;
import com.tripbler.backend.user.dto.UserCreateRequest;
import com.tripbler.backend.user.dto.UserPasswordChangeRequest;
import com.tripbler.backend.user.dto.UserResponse;
import com.tripbler.backend.user.dto.UserNicknameChangeRequest;
import com.tripbler.backend.user.entity.User;
import com.tripbler.backend.user.exception.CurrentPasswordMismatchException;
import com.tripbler.backend.user.exception.DuplicateLoginIdException;
import com.tripbler.backend.user.exception.DuplicateNicknameException;
import com.tripbler.backend.user.exception.DuplicateUserFieldException;
import com.tripbler.backend.user.repository.UserRepository;
import com.tripbler.backend.user.storage.ProfileImageStorage;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DataIntegrityViolationException;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserFinder userFinder;
    private final ProfileImageStorage profileImageStorage;

    public UserService(
        UserRepository userRepository,
        PasswordEncoder passwordEncoder,
        UserFinder userFinder,
        ProfileImageStorage profileImageStorage
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.userFinder = userFinder;
        this.profileImageStorage = profileImageStorage;
    }

    @Transactional
    public UserResponse createUser(UserCreateRequest request) {

        if (userRepository.findByLoginId(request.loginId()).isPresent()) {
            throw new DuplicateLoginIdException();
        }

        if (request.nickname() != null &&
            userRepository.existsByNickname(request.nickname())) {
            throw new DuplicateNicknameException();
        }

        String encodedPassword =
            passwordEncoder.encode(request.password());

        User user = new User(
            request.loginId(),
            request.nickname(),
            encodedPassword
        );

        try {
            User savedUser =
                userRepository.saveAndFlush(user);

            return UserResponse.from(savedUser);
        } catch (
            DataIntegrityViolationException exception
        ) {
            throw new DuplicateUserFieldException(
                exception
            );
        }
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

        User user =
            userFinder.getById(userId);

        return toUserResponse(user);
    }

    @Transactional
    public UserResponse changeNickname(
        Long userId,
        UserNicknameChangeRequest request
    ) {
        User user = userFinder.getById(userId);

        if (userRepository.existsByNicknameAndIdNot(
            request.nickname(),
            userId
        )) {
            throw new DuplicateNicknameException();
        }

        user.changeNickname(
            request.nickname()
        );

        try {
            userRepository.flush();
        } catch (
            DataIntegrityViolationException exception
        ) {
            throw new DuplicateNicknameException(
                exception
            );
        }

        return toUserResponse(user);
    }

    @Transactional
    public void changePassword(
        Long userId,
        UserPasswordChangeRequest request
    ) {
        User user = userFinder.getById(userId);

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

    private UserResponse toUserResponse(
        User user
    ) {
        String profileImageUrl =
            profileImageStorage.resolveUrl(
                user.getProfileImageKey()
            );

        return UserResponse.from(
            user,
            profileImageUrl
        );
    }
}