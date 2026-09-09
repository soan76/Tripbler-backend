package com.tripbler.backend.common.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.RestController;

import com.tripbler.backend.common.security.CustomAccessDeniedHandler;
import com.tripbler.backend.common.security.CustomAuthenticationEntryPoint;

@WebMvcTest(
    controllers =
        WebConfigTest.TestController.class
)
@Import({
    WebConfig.class,
    SecurityConfig.class,
    CustomAuthenticationEntryPoint.class,
    CustomAccessDeniedHandler.class
})
@TestPropertySource(
    properties = {
        "profile-image.storage.local-root=./build/test-uploads"
    }
)
class WebConfigTest {

    private static final Path TEST_ROOT =
        Path.of("./build/test-uploads")
            .toAbsolutePath()
            .normalize();

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @BeforeEach
    void setUp() throws IOException {
        Files.createDirectories(
            TEST_ROOT.resolve("profiles/1")
        );
    }

    @AfterEach
    void tearDown() throws IOException {
        if (!Files.exists(TEST_ROOT)) {
            return;
        }

        try (
            var paths = Files.walk(TEST_ROOT)
        ) {
            paths
                .sorted(
                    (first, second) ->
                        second.compareTo(first)
                )
                .forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException exception) {
                        throw new RuntimeException(
                            exception
                        );
                    }
                });
        }
    }

    @Test
    @DisplayName(
        "/uploads/** 요청은 로컬에 저장된 파일을 HTTP로 반환한다"
    )
    void uploadedFileCanBeAccessedViaHttp()
        throws Exception {

        byte[] imageContent =
            "test-image-data".getBytes();

        Path imagePath =
            TEST_ROOT.resolve(
                "profiles/1/profile.jpg"
            );

        Files.write(
            imagePath,
            imageContent
        );

        mockMvc.perform(
                get(
                    "/uploads/profiles/1/profile.jpg"
                )
            )
            .andExpect(status().isOk())
            .andExpect(
                content().bytes(
                    imageContent
                )
            );
    }

    @Test
    @DisplayName(
        "존재하지 않는 /uploads/** 파일을 요청하면 404를 반환한다"
    )
    void nonexistentUploadedFileReturnsNotFound()
        throws Exception {

        mockMvc.perform(
                get(
                    "/uploads/profiles/1/not-found.jpg"
                )
            )
            .andExpect(
                status().isNotFound()
            );
    }

    @RestController
    static class TestController {
    }
}