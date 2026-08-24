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
                    .requestMatchers(
                        HttpMethod.POST,
                        "/api/v1/users"
                    ).permitAll()

                    .requestMatchers(
                        HttpMethod.POST,
                        "/api/v1/auth/logout"
                    ).authenticated()

                    .requestMatchers(
                        "/api/v1/auth/**"
                    ).permitAll()

                    .requestMatchers(
                        "/api/v1/exchange/**"
                    ).permitAll()

                    .requestMatchers(
                        "/api/v1/places/**"
                    ).permitAll()

                    .requestMatchers(
                        "/api/v1/translation/**"
                    ).permitAll()

                    .requestMatchers(
                        "/api/v1/admin/**"
                    ).hasRole("ADMIN")

                    .requestMatchers(
                        "/api/v1/users/me"
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