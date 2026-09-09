package com.tripbler.backend.user.service;

import com.tripbler.backend.user.entity.User;
import com.tripbler.backend.user.exception.UserNotFoundException;
import com.tripbler.backend.user.repository.UserRepository;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class UserFinder {

    private final UserRepository userRepository;

    public UserFinder(
        UserRepository userRepository
    ) {
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public User getById(
        Long userId
    ) {
        return userRepository.findById(
            userId
        ).orElseThrow(
            UserNotFoundException::new
        );
    }
}