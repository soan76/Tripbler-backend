package com.tripbler.backend.common.config;

import java.util.Base64;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

@Configuration
public class JwtConfig {

    @Value("${security.jwt.secret}")
    private String jwtSecret;

    @Bean
    public SecretKey jwtSecretKey() {
        byte[] keyBytes = Base64
            .getDecoder()
            .decode(jwtSecret);

        if (keyBytes.length < 32) {
            throw new IllegalStateException(
                "JWT 비밀키는 최소 256비트 이상이어야 합니다."
            );
        }

        return new SecretKeySpec(
            keyBytes,
            "HmacSHA256"
        );
    }

    @Bean
    public JwtEncoder jwtEncoder(
        SecretKey jwtSecretKey
    ) {
        return NimbusJwtEncoder
            .withSecretKey(jwtSecretKey)
            .algorithm(MacAlgorithm.HS256)
            .build();
    }

    @Bean
    public JwtDecoder jwtDecoder(
        SecretKey jwtSecretKey
    ) {
        return NimbusJwtDecoder
            .withSecretKey(jwtSecretKey)
            .macAlgorithm(MacAlgorithm.HS256)
            .build();
    }
}