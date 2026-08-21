package com.tripbler.backend.auth.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import com.tripbler.backend.user.entity.UserRole;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Service;

@Service
public class JwtTokenService {

    private final JwtEncoder jwtEncoder;
    private final long accessTokenExpirationSeconds;

    public JwtTokenService(
        JwtEncoder jwtEncoder,
        @Value(
            "${security.jwt.access-token-expiration-seconds}"
        )
        long accessTokenExpirationSeconds
    ) {
        this.jwtEncoder = jwtEncoder;
        this.accessTokenExpirationSeconds =
            accessTokenExpirationSeconds;
    }

    public String createAccessToken(
        Long userId,
        String email,
        UserRole role
    ) {
        Instant now = Instant.now();

        Instant expiresAt = now.plus(
            accessTokenExpirationSeconds,
            ChronoUnit.SECONDS
        );

        JwtClaimsSet claims = JwtClaimsSet.builder()
            .subject(userId.toString())
            .issuedAt(now)
            .expiresAt(expiresAt)
            .claim("email", email)
            .claim(
                "roles",
                List.of(role.name())
            )
            .build();

        JwsHeader header = JwsHeader
            .with(MacAlgorithm.HS256)
            .build();

        return jwtEncoder
            .encode(
                JwtEncoderParameters.from(
                    header,
                    claims
                )
            )
            .getTokenValue();
    }
}