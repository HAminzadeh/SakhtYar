package com.sakhtyar.auth.api;

import com.sakhtyar.identity.domain.UserEntity;
import com.sakhtyar.identity.domain.UserRepository;
import com.sakhtyar.identity.security.JwtCookieAuthenticationFilter;
import com.sakhtyar.identity.security.JwtService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.Duration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final UserRepository userRepository;

    public AuthController(
            AuthenticationManager authenticationManager,
            JwtService jwtService,
            UserRepository userRepository
    ) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.userRepository = userRepository;
    }

    @PostMapping("/login")
    public MeResponse login(
            @Valid @RequestBody LoginRequest request,
            HttpServletResponse response
    ) {
        Authentication auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.username(),
                        request.password()
                )
        );

        UserEntity user = userRepository
                .findByUsernameIgnoreCase(auth.getName())
                .orElseThrow();

        String token = jwtService.createToken(
                user.getUsername(),
                user.getRole().name()
        );

        ResponseCookie cookie = ResponseCookie
                .from(JwtCookieAuthenticationFilter.COOKIE_NAME, token)
                .httpOnly(true)
                .secure(false)
                .sameSite("Strict")
                .path("/")
                .maxAge(Duration.ofSeconds(jwtService.expirationSeconds()))
                .build();

        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
        return MeResponse.from(user);
    }

    @GetMapping("/me")
    public MeResponse me(Authentication authentication) {
        UserEntity user = userRepository
                .findByUsernameIgnoreCase(authentication.getName())
                .orElseThrow();

        return MeResponse.from(user);
    }

    @PostMapping("/logout")
    public void logout(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie
                .from(JwtCookieAuthenticationFilter.COOKIE_NAME, "")
                .httpOnly(true)
                .secure(false)
                .sameSite("Strict")
                .path("/")
                .maxAge(Duration.ZERO)
                .build();

        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    public record LoginRequest(
            @NotBlank String username,
            @NotBlank String password
    ) {
    }

    public record MeResponse(
            String username,
            String displayName,
            String role
    ) {
        static MeResponse from(UserEntity user) {
            return new MeResponse(
                    user.getUsername(),
                    user.getDisplayName(),
                    user.getRole().name()
            );
        }
    }
}
