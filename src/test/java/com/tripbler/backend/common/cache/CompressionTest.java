package com.tripbler.backend.common.cache;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.bind.annotation.*;

/** 실제 embedded HTTP 서버에서 application.properties의 gzip 설정을 검증한다. */
@SpringBootTest(classes = CompressionTest.App.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CompressionTest {
    @Value("${local.server.port}") int port;
    @SpringBootConfiguration
    @EnableAutoConfiguration(excludeName = "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration")
    @Import(Payload.class)
    static class App {
        @Bean SecurityFilterChain testSecurity(HttpSecurity http) throws Exception {
            return http.authorizeHttpRequests(requests -> requests.anyRequest().permitAll()).build();
        }
    }
    @RestController static class Payload {
        @GetMapping("/compression-probe") Map<String, String> get() { return Map.of("history", "1234567890".repeat(1000)); }
    }
    @Test void jsonIsCompressedOnlyWhenRequested() throws Exception {
        var client = HttpClient.newHttpClient();
        var uri = URI.create("http://localhost:" + port + "/compression-probe");
        var plain = client.send(HttpRequest.newBuilder(uri).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
        var gzip = client.send(HttpRequest.newBuilder(uri).header("Accept-Encoding", "gzip").GET().build(), HttpResponse.BodyHandlers.ofByteArray());
        assertEquals(200, plain.statusCode());
        assertEquals(200, gzip.statusCode());
        assertEquals("gzip", gzip.headers().firstValue("Content-Encoding").orElseThrow());
        assertTrue(gzip.body().length < plain.body().length);
        try (var input = new GZIPInputStream(new ByteArrayInputStream(gzip.body()))) {
            assertEquals(new String(plain.body(), StandardCharsets.UTF_8), new String(input.readAllBytes(), StandardCharsets.UTF_8));
        }
    }
}
