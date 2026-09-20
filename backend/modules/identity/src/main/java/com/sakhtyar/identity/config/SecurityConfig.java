package com.sakhtyar.identity.config;

import com.sakhtyar.identity.application.DatabaseUserDetailsService;
import com.sakhtyar.identity.security.JwtCookieAuthenticationFilter;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
public class SecurityConfig {

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    DaoAuthenticationProvider authenticationProvider(
            DatabaseUserDetailsService userDetailsService,
            PasswordEncoder passwordEncoder
    ) {
        DaoAuthenticationProvider provider =
                new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return provider;
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(
            @Value("${app.cors.allowed-origins}") String allowedOrigins
    ) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(
                Arrays.stream(allowedOrigins.split(","))
                        .map(String::trim)
                        .filter(value -> !value.isBlank())
                        .toList()
        );
        config.setAllowedMethods(
                List.of(
                        "GET",
                        "POST",
                        "PUT",
                        "PATCH",
                        "DELETE",
                        "OPTIONS"
                )
        );
        config.setAllowedHeaders(
                List.of(
                        "Content-Type",
                        "Accept",
                        "Authorization",
                        "X-Requested-With",
                        "X-XSRF-TOKEN"
                )
        );
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source =
                new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtCookieAuthenticationFilter jwtFilter,
            DaoAuthenticationProvider authenticationProvider
    ) throws Exception {

        CookieCsrfTokenRepository csrfRepository =
                CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrfRepository.setCookiePath("/");

        http
                .cors(Customizer.withDefaults())
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfRepository)
                        .ignoringRequestMatchers(
                                "/api/v1/auth/login",
                                "/api/v1/auth/register",
                                "/api/v1/auth/mobile/**"
                        )
                )
                .sessionManagement(session ->
                        session.sessionCreationPolicy(
                                SessionCreationPolicy.STATELESS
                        )
                )
                .authenticationProvider(authenticationProvider)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/api/v1/health",
                                "/actuator/health",
                                "/actuator/info"
                        ).permitAll()

                        .requestMatchers(
                                "/api/v1/auth/login",
                                "/api/v1/auth/register",
                                "/api/v1/auth/refresh",
                                "/api/v1/auth/csrf",
                                "/api/v1/auth/mobile/**"
                        ).permitAll()

                        .requestMatchers(
                                "/api/v1/admin/users/**"
                        ).hasAuthority("USER_MANAGE")

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/agents/glossary/*/approve",
                                "/api/v1/agents/glossary/*/reject"
                        ).hasAuthority("GLOSSARY_MANAGE")

                        .requestMatchers(
                                "/api/v1/agents/**"
                        ).hasAuthority("AGENT_USE")

                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/documents/**"
                        ).hasAuthority("DOCUMENT_READ")

                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/cases/*/documents"
                        ).hasAuthority("DOCUMENT_READ")

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/cases/*/documents"
                        ).hasAuthority("DOCUMENT_WRITE")

                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/cases/*/owners/**"
                        ).hasAuthority("OWNER_READ")

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/cases/*/owners/**"
                        ).hasAuthority("OWNER_WRITE")

                        .requestMatchers(
                                HttpMethod.PUT,
                                "/api/v1/cases/*/owners/**"
                        ).hasAuthority("OWNER_WRITE")

                        .requestMatchers(
                                HttpMethod.DELETE,
                                "/api/v1/cases/*/owners/**"
                        ).hasAuthority("OWNER_WRITE")

                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/cases/*/property/**"
                        ).hasAuthority("PROPERTY_READ")

                        .requestMatchers(
                                HttpMethod.PUT,
                                "/api/v1/cases/*/property/**"
                        ).hasAuthority("PROPERTY_WRITE")

                        .requestMatchers(
                                HttpMethod.PATCH,
                                "/api/v1/cases/*/property/**"
                        ).hasAuthority("PROPERTY_WRITE")

                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/geo/**"
                        ).hasAuthority("CASE_READ")

                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/cases/**"
                        ).hasAuthority("CASE_READ")

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/cases/**"
                        ).hasAuthority("CASE_WRITE")

                        .requestMatchers(
                                HttpMethod.PUT,
                                "/api/v1/cases/**"
                        ).hasAuthority("CASE_WRITE")

                        .requestMatchers(
                                HttpMethod.PATCH,
                                "/api/v1/cases/**"
                        ).hasAuthority("CASE_WRITE")

                        .requestMatchers(
                                HttpMethod.DELETE,
                                "/api/v1/cases/**"
                        ).hasAuthority("CASE_WRITE")

                        .requestMatchers(
                                HttpMethod.OPTIONS,
                                "/**"
                        ).permitAll()

                        .anyRequest().authenticated()
                )
                .addFilterBefore(
                        jwtFilter,
                        UsernamePasswordAuthenticationFilter.class
                );

        return http.build();
    }
}
