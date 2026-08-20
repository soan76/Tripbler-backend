package com.tripbler.backend.user.service;

import com.tripbler.backend.user.dto.UserCreateRequest;
import com.tripbler.backend.user.dto.UserResponse;
import com.tripbler.backend.user.entity.User;
import com.tripbler.backend.user.exception.DuplicateEmailException;
import com.tripbler.backend.user.repository.UserRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional
    public UserResponse createUser(UserCreateRequest request) {

        if (userRepository.findByEmail(request.email()).isPresent()) {
            throw new DuplicateEmailException();
        }

        User user = new User(request.email());

        User savedUser = userRepository.save(user);

        return UserResponse.from(savedUser);
    }
}