package com.tripbler.backend.common.exception;

import java.time.LocalDate;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import jakarta.servlet.http.HttpServletRequest;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log =
        LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(
        BusinessException exception,
        HttpServletRequest request
    ) {
        ErrorCode errorCode = exception.getErrorCode();

        log.warn(
            "Business exception: code={}, message={}, path={}",
            errorCode.getCode(),
            exception.getMessage(),
            request.getRequestURI()
        );

        ErrorResponse response = ErrorResponse.of(
            errorCode,
            exception.getMessage(),
            request.getRequestURI()
        );

        return ResponseEntity
            .status(errorCode.getStatus())
            .body(response);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErrorResponse> handleMethodValidationException(
        HandlerMethodValidationException exception,
        HttpServletRequest request
    ) {
        String message = exception
            .getAllErrors()
            .stream()
            .map(error -> error.getDefaultMessage())
            .filter(errorMessage -> errorMessage != null)
            .distinct()
            .collect(Collectors.joining(", "));

        if (message.isBlank()) {
            message = ErrorCode.INVALID_REQUEST.getMessage();
        }

        ErrorResponse response = ErrorResponse.of(
            ErrorCode.INVALID_REQUEST,
            message,
            request.getRequestURI()
        );

        return ResponseEntity
            .badRequest()
            .body(response);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgumentException(
        IllegalArgumentException exception,
        HttpServletRequest request
    ) {
        ErrorResponse response = ErrorResponse.of(
            ErrorCode.INVALID_REQUEST,
            exception.getMessage(),
            request.getRequestURI()
        );

        return ResponseEntity
            .badRequest()
            .body(response);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpectedException(
        Exception exception,
        HttpServletRequest request
    ) {
        log.error(
            "Unexpected exception: path={}",
            request.getRequestURI(),
            exception
        );

        ErrorResponse response = ErrorResponse.of(
            ErrorCode.INTERNAL_SERVER_ERROR,
            request.getRequestURI()
        );

        return ResponseEntity
            .status(ErrorCode.INTERNAL_SERVER_ERROR.getStatus())
            .body(response);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatchException(
        MethodArgumentTypeMismatchException exception,
        HttpServletRequest request
    ) {
        String message;

        if (
            exception.getRequiredType() != null
                && exception.getRequiredType().equals(LocalDate.class)
        ) {
            message = "날짜는 YYYY-MM-DD 형식이어야 합니다.";
        } else {
            message = "요청 파라미터 형식이 올바르지 않습니다.";
        }

        ErrorResponse response = ErrorResponse.of(
            ErrorCode.INVALID_REQUEST,
            message,
            request.getRequestURI()
        );

        return ResponseEntity
            .badRequest()
            .body(response);
    }
}