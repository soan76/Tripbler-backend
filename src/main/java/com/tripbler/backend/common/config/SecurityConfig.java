package com.tripbler.backend.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;

import com.tripbler.backend.common.security.CustomAccessDeniedHandler;
import com.tripbler.backend.common.security.CustomAuthenticationEntryPoint;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final CustomAuthenticationEntryPoint authenticationEntryPoint;
    private final CustomAccessDeniedHandler accessDeniedHandler;

    public SecurityConfig(
        CustomAuthenticationEntryPoint authenticationEntryPoint,
        CustomAccessDeniedHandler accessDeniedHandler
    ) {
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.accessDeniedHandler = accessDeniedHandler;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
        HttpSecurity http
    ) throws Exception {

        http
            .csrf(csrf -> csrf.disable())

            .sessionManagement(session ->
                session.sessionCreationPolicy(
                    SessionCreationPolicy.STATELESS
                )
            )

            .authorizeHttpRequests(authorize ->
                authorize
                    // 회원가입
                    .requestMatchers(
                        HttpMethod.POST,
                        "/api/v1/users"
                    ).permitAll()

                    // 로그아웃은 JWT 인증 필요
                    .requestMatchers(
                        HttpMethod.POST,
                        "/api/v1/auth/logout"
                    ).authenticated()

                    // 로그인 / Refresh 등 인증 API
                    .requestMatchers(
                        "/api/v1/auth/**"
                    ).permitAll()

                    .requestMatchers(
                        "/api/v1/exchange/**"
                    ).permitAll()

                    // 암호화폐 현재가/History 조회는 기존 환율 조회처럼 공개한다.
                    .requestMatchers(
                        HttpMethod.GET,
                        "/api/v1/crypto/**"
                    ).permitAll()

                    // 장소 검색은 로그인 사용자만 쓴다(유료 Google 호출 남용 방지).
                    // 아래 /api/v1/places/** 공개 규칙보다 먼저 와야 한다.
                    .requestMatchers(
                        "/api/v1/places/search"
                    ).authenticated()

                    // 주변 장소, 최근접 장소, 장소 상세는 공개한다.
                    .requestMatchers(
                        "/api/v1/places/**"
                    ).permitAll()

                    .requestMatchers(
                        "/api/v1/translation/**"
                    ).permitAll()

                    // 프로필 이미지 정적 리소스는 인증 없이 조회할 수 있다.
                    .requestMatchers(
                        HttpMethod.GET,
                        "/uploads/**"
                    ).permitAll()

                    .requestMatchers(
                        "/api/v1/admin/**"
                    ).hasRole("ADMIN")

                    // 현재 사용자 관련 API는 JWT 인증 필요
                    .requestMatchers(
                        "/api/v1/users/me",
                        "/api/v1/users/me/**"
                    ).authenticated()

                    .anyRequest().permitAll()
            )

            .exceptionHandling(exception ->
                exception
                    .authenticationEntryPoint(
                        authenticationEntryPoint
                    )
                    .accessDeniedHandler(
                        accessDeniedHandler
                    )
            )

            .oauth2ResourceServer(oauth2 ->
                oauth2
                    .jwt(jwt ->
                        jwt.jwtAuthenticationConverter(
                            jwtAuthenticationConverter()
                        )
                    )
                    .authenticationEntryPoint(
                        authenticationEntryPoint
                    )
                    .accessDeniedHandler(
                        accessDeniedHandler
                    )
            );

        return http.build();
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {

        JwtGrantedAuthoritiesConverter authoritiesConverter =
            new JwtGrantedAuthoritiesConverter();

        authoritiesConverter.setAuthoritiesClaimName("roles");
        authoritiesConverter.setAuthorityPrefix("ROLE_");

        JwtAuthenticationConverter authenticationConverter =
            new JwtAuthenticationConverter();

        authenticationConverter.setJwtGrantedAuthoritiesConverter(
            authoritiesConverter
        );

        return authenticationConverter;
    }
}
