package com.tripbler.backend.common.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import com.tripbler.backend.common.exception.ErrorCode;
import com.tripbler.backend.common.exception.ErrorResponse;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.ObjectMapper;

@Component
public class CustomAccessDeniedHandler
    implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public CustomAccessDeniedHandler(
        ObjectMapper objectMapper
    ) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(
        HttpServletRequest request,
        HttpServletResponse response,
        AccessDeniedException accessDeniedException
    ) throws IOException, ServletException {

        ErrorResponse errorResponse = ErrorResponse.of(
            ErrorCode.FORBIDDEN,
            request.getRequestURI()
        );

        response.setStatus(
            ErrorCode.FORBIDDEN.getStatus().value()
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