package com.tripbler.backend.common.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import com.tripbler.backend.common.exception.ErrorCode;
import com.tripbler.backend.common.exception.ErrorResponse;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.ObjectMapper;

@Component
public class CustomAuthenticationEntryPoint
    implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    public CustomAuthenticationEntryPoint(
        ObjectMapper objectMapper
    ) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(
        HttpServletRequest request,
        HttpServletResponse response,
        AuthenticationException authException
    ) throws IOException, ServletException {
        System.out.println("=== CustomAuthenticationEntryPoint 실행됨 ===");

        ErrorResponse errorResponse = ErrorResponse.of(
            ErrorCode.UNAUTHORIZED,
            request.getRequestURI()
        );

        response.setStatus(
            ErrorCode.UNAUTHORIZED.getStatus().value()
        );

        response.setContentType(
            MediaType.APPLICATION_JSON_VALUE
        );

        response.setCharacterEncoding(
            StandardCharsets.UTF_8.name()
        );

        objectMapper.writeValue(
            response.getWriter(),
            errorResponse
        );
    }
}