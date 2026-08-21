package com.tripbler.backend.user.service;

import com.tripbler.backend.user.dto.UserCreateRequest;
import com.tripbler.backend.user.dto.UserResponse;
import com.tripbler.backend.user.entity.User;
import com.tripbler.backend.user.exception.DuplicateEmailException;
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

        if (userRepository.findByEmail(request.email()).isPresent()) {
            throw new DuplicateEmailException();
        }

        String encodedPassword =
            passwordEncoder.encode(request.password());

        User user = new User(
            request.email(),
            encodedPassword
        );

        User savedUser = userRepository.save(user);

        return UserResponse.from(savedUser);
    }
}