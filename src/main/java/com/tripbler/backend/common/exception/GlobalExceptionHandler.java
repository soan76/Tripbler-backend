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
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

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

        if (errorCode.getStatus().is5xxServerError()) {
            log.error(
                "Business exception: code={}, message={}, path={}",
                errorCode.getCode(),
                exception.getMessage(),
                request.getRequestURI(),
                exception
            );
        } else {
            log.warn(
                "Business exception: code={}, message={}, path={}",
                errorCode.getCode(),
                exception.getMessage(),
                request.getRequestURI()
            );
        }

        ErrorResponse response = ErrorResponse.of(
            errorCode,
            exception.getMessage(),
            request.getRequestURI()
        );

        return ResponseEntity
            .status(errorCode.getStatus())
            .body(response);
    }

    // 메서드 파라미터 검증 오류를 처리한다.
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
    // @Valid 요청 본문 검증 오류를 처리한다.
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValidException(
        MethodArgumentNotValidException exception,
        HttpServletRequest request
    ) {
        String message = exception
            .getBindingResult()
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

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResourceFoundException(
        NoResourceFoundException exception,
        HttpServletRequest request
    ) {
        ErrorResponse response = ErrorResponse.of(
            ErrorCode.RESOURCE_NOT_FOUND,
            request.getRequestURI()
        );

        return ResponseEntity
            .status(ErrorCode.RESOURCE_NOT_FOUND.getStatus())
            .body(response);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse>
        handleMaxUploadSizeExceededException(
            MaxUploadSizeExceededException exception,
            HttpServletRequest request
        ) {

        String message =
            "프로필 이미지는 5MB 이하만 업로드할 수 있습니다.";

        ErrorResponse response = ErrorResponse.of(
            ErrorCode.INVALID_PROFILE_IMAGE,
            message,
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

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleHttpRequestMethodNotSupported(
        HttpRequestMethodNotSupportedException exception,
        HttpServletRequest request
    ) {
        ErrorResponse response = ErrorResponse.of(
            ErrorCode.METHOD_NOT_ALLOWED,
            request.getRequestURI()
        );

        return ResponseEntity
            .status(ErrorCode.METHOD_NOT_ALLOWED.getStatus())
            .body(response);
    }
}