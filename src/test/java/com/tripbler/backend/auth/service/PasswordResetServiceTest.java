package com.tripbler.backend.auth.service;

import com.tripbler.backend.common.exception.BusinessException;
import com.tripbler.backend.common.exception.ErrorCode;
import com.tripbler.backend.user.entity.User;
import com.tripbler.backend.user.repository.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

    @Mock
    private PasswordResetTokenService resetTokenService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private PasswordResetService service;

    @BeforeEach
    void setUp() {
        service = new PasswordResetService(
            resetTokenService,
            userRepository,
            passwordEncoder
        );
    }

    @Test
    void resetPasswordSucceeds() {
        String resetToken = "valid-reset-token";
        String newPassword = "Tripbler456!";

        User user = new User(
            "testuser",
            "테스트사용자",
            "encoded-current-password"
        );

        when(
            resetTokenService
                .validateResetToken(resetToken)
        ).thenReturn(1L);

        when(userRepository.findById(1L))
            .thenReturn(Optional.of(user));

        when(
            passwordEncoder.matches(
                newPassword,
                "encoded-current-password"
            )
        ).thenReturn(false);

        when(
            passwordEncoder.encode(newPassword)
        ).thenReturn("encoded-new-password");

        service.resetPassword(
            resetToken,
            newPassword
        );

        assertEquals(
            "encoded-new-password",
            user.getPassword()
        );

        verify(passwordEncoder)
            .encode(newPassword);

        verify(resetTokenService)
            .markTokenUsed(resetToken);
    }

    @Test
    void resetPasswordFailsWhenNewPasswordIsSameAsCurrent() {
        String resetToken = "valid-reset-token";
        String samePassword = "Tripbler123!";

        User user = new User(
            "testuser",
            "테스트사용자",
            "encoded-current-password"
        );

        when(
            resetTokenService
                .validateResetToken(resetToken)
        ).thenReturn(1L);

        when(userRepository.findById(1L))
            .thenReturn(Optional.of(user));

        when(
            passwordEncoder.matches(
                samePassword,
                "encoded-current-password"
            )
        ).thenReturn(true);

        BusinessException exception =
            assertThrows(
                BusinessException.class,
                () -> service.resetPassword(
                    resetToken,
                    samePassword
                )
            );

        assertEquals(
            ErrorCode.NEW_PASSWORD_SAME_AS_CURRENT,
            exception.getErrorCode()
        );

        assertEquals(
            "encoded-current-password",
            user.getPassword()
        );

        verify(
            passwordEncoder,
            never()
        ).encode(anyString());

        verify(
            resetTokenService,
            never()
        ).markTokenUsed(anyString());
    }

    @Test
    void resetPasswordFailsWhenUserDoesNotExist() {
        String resetToken = "valid-reset-token";

        when(
            resetTokenService
                .validateResetToken(resetToken)
        ).thenReturn(999L);

        when(userRepository.findById(999L))
            .thenReturn(Optional.empty());

        BusinessException exception =
            assertThrows(
                BusinessException.class,
                () -> service.resetPassword(
                    resetToken,
                    "Tripbler456!"
                )
            );

        assertEquals(
            ErrorCode.USER_NOT_FOUND,
            exception.getErrorCode()
        );

        verifyNoInteractions(passwordEncoder);

        verify(
            resetTokenService,
            never()
        ).markTokenUsed(anyString());
    }
}