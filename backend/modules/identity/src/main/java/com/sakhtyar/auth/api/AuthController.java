package com.sakhtyar.auth.api;

import com.sakhtyar.auth.api.AuthDtos.ChangePasswordRequest;
import com.sakhtyar.auth.api.AuthDtos.CsrfResponse;
import com.sakhtyar.auth.api.AuthDtos.LoginRequest;
import com.sakhtyar.auth.api.AuthDtos.MeResponse;
import com.sakhtyar.auth.api.AuthDtos.MobileLoginRequest;
import com.sakhtyar.auth.api.AuthDtos.MobileLogoutRequest;
import com.sakhtyar.auth.api.AuthDtos.MobileRefreshRequest;
import com.sakhtyar.auth.api.AuthDtos.MobileTokenResponse;
import com.sakhtyar.auth.api.AuthDtos.ProfileUpdateRequest;
import com.sakhtyar.auth.api.AuthDtos.RegisterRequest;
import com.sakhtyar.auth.api.AuthDtos.RegistrationResponse;
import com.sakhtyar.auth.api.AuthDtos.SessionResponse;
import com.sakhtyar.identity.application.AuthService;
import com.sakhtyar.identity.application.AuthSessionService;
import com.sakhtyar.identity.application.AuthSessionService.CreatedSession;
import com.sakhtyar.identity.application.AuthSessionService.RotatedSession;
import com.sakhtyar.identity.domain.AuthSessionEntity;
import com.sakhtyar.identity.domain.UserEntity;
import com.sakhtyar.identity.security.JwtCookieAuthenticationFilter;
import com.sakhtyar.identity.security.JwtService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import com.sakhtyar.identity.mapper.UserMapper;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final AuthSessionService sessionService;
    private final JwtService jwtService;
    private final boolean secureCookies;
    private final String sameSite;
    private final UserMapper userMapper;

    public AuthController(
            AuthService authService,
            AuthSessionService sessionService,
            JwtService jwtService,
            @Value("${app.security.cookies.secure:false}")
            boolean secureCookies,
            @Value("${app.security.cookies.same-site:Strict}")
            String sameSite,
            UserMapper userMapper) {
        this.userMapper = userMapper;
        this.authService = authService;
        this.sessionService = sessionService;
        this.jwtService = jwtService;
        this.secureCookies = secureCookies;
        this.sameSite = sameSite;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public RegistrationResponse register(
            @Valid @RequestBody RegisterRequest request
    ) {
        UserEntity user = authService.register(request);

        return new RegistrationResponse(
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                user.getStatus().name(),
                "ثبت‌نام انجام شد و حساب در انتظار تأیید مدیر سیستم است."
        );
    }

    @PostMapping("/login")
    public MeResponse login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest servletRequest,
            HttpServletResponse response
    ) {
        UserEntity user = authService.authenticate(
                request.username(),
                request.password()
        );

        CreatedSession session = sessionService.create(
                user.getId(),
                "WEB",
                null,
                servletRequest.getHeader("User-Agent"),
                clientIp(servletRequest)
        );

        issueWebCookies(user, session, response);
        return userMapper.toMeResponse(user);
    }

    @PostMapping("/refresh")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void refresh(
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        String refreshToken =
                JwtCookieAuthenticationFilter.extractCookie(
                        request,
                        JwtCookieAuthenticationFilter.REFRESH_COOKIE_NAME
                );

        RotatedSession rotated = sessionService.rotate(refreshToken);
        UserEntity user = authService.requireById(
                rotated.session().getUserId()
        );

        if (!user.isEnabledForLogin()) {
            sessionService.revokeByToken(rotated.refreshToken());
            clearWebCookies(response);
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "حساب کاربری فعال نیست."
            );
        }

        issueWebCookies(user, rotated, response);
    }

    @GetMapping("/me")
    public MeResponse me(Authentication authentication) {
        return userMapper.toMeResponse(
                authService.requireByUsername(authentication.getName())
        );
    }

    @PutMapping("/profile")
    public MeResponse updateProfile(
            Authentication authentication,
            @Valid @RequestBody ProfileUpdateRequest request
    ) {
        return userMapper.toMeResponse(
                authService.updateProfile(
                        authentication.getName(),
                        request
                )
        );
    }

    @PostMapping("/change-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(
            Authentication authentication,
            @Valid @RequestBody ChangePasswordRequest request,
            HttpServletResponse response
    ) {
        UserEntity user = authService.requireByUsername(
                authentication.getName()
        );

        authService.changePassword(
                authentication.getName(),
                request.currentPassword(),
                request.newPassword()
        );

        sessionService.revokeAll(user.getId());
        clearWebCookies(response);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        String refreshToken =
                JwtCookieAuthenticationFilter.extractCookie(
                        request,
                        JwtCookieAuthenticationFilter.REFRESH_COOKIE_NAME
                );

        sessionService.revokeByToken(refreshToken);
        clearWebCookies(response);
    }

    @GetMapping("/sessions")
    public List<SessionResponse> sessions(Authentication authentication) {
        UserEntity user = authService.requireByUsername(
                authentication.getName()
        );

        return sessionService.list(user.getId())
                .stream()
                .map(AuthController::sessionResponse)
                .toList();
    }

    @DeleteMapping("/sessions/{sessionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokeSession(
            Authentication authentication,
            @PathVariable UUID sessionId
    ) {
        UserEntity user = authService.requireByUsername(
                authentication.getName()
        );
        sessionService.revokeSession(user.getId(), sessionId);
    }

    @DeleteMapping("/sessions")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokeAllSessions(
            Authentication authentication,
            HttpServletResponse response
    ) {
        UserEntity user = authService.requireByUsername(
                authentication.getName()
        );
        sessionService.revokeAll(user.getId());
        clearWebCookies(response);
    }

    @GetMapping("/csrf")
    public CsrfResponse csrf(CsrfToken token) {
        return new CsrfResponse(
                token.getHeaderName(),
                token.getParameterName(),
                token.getToken()
        );
    }

    @PostMapping("/mobile/login")
    public MobileTokenResponse mobileLogin(
            @Valid @RequestBody MobileLoginRequest request,
            HttpServletRequest servletRequest
    ) {
        UserEntity user = authService.authenticate(
                request.username(),
                request.password()
        );

        CreatedSession session = sessionService.create(
                user.getId(),
                "MOBILE",
                request.deviceName(),
                servletRequest.getHeader("User-Agent"),
                clientIp(servletRequest)
        );

        return mobileResponse(user, session);
    }

    @PostMapping("/mobile/refresh")
    public MobileTokenResponse mobileRefresh(
            @Valid @RequestBody MobileRefreshRequest request
    ) {
        RotatedSession rotated =
                sessionService.rotate(request.refreshToken());

        UserEntity user = authService.requireById(
                rotated.session().getUserId()
        );

        if (!user.isEnabledForLogin()) {
            sessionService.revokeByToken(rotated.refreshToken());
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "حساب کاربری فعال نیست."
            );
        }

        return mobileResponse(user, rotated);
    }

    @PostMapping("/mobile/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void mobileLogout(
            @Valid @RequestBody MobileLogoutRequest request
    ) {
        sessionService.revokeByToken(request.refreshToken());
    }

    private void issueWebCookies(
            UserEntity user,
            CreatedSession session,
            HttpServletResponse response
    ) {
        addAccessCookie(user, session.session().getId(), response);
        addRefreshCookie(
                session.refreshToken(),
                session.session().getExpiresAt(),
                response
        );
    }

    private void issueWebCookies(
            UserEntity user,
            RotatedSession session,
            HttpServletResponse response
    ) {
        addAccessCookie(user, session.session().getId(), response);
        addRefreshCookie(
                session.refreshToken(),
                session.session().getExpiresAt(),
                response
        );
    }

    private void addAccessCookie(
            UserEntity user,
            UUID sessionId,
            HttpServletResponse response
    ) {
        String token = jwtService.createAccessToken(user, sessionId);

        ResponseCookie cookie = ResponseCookie
                .from(
                        JwtCookieAuthenticationFilter.ACCESS_COOKIE_NAME,
                        token
                )
                .httpOnly(true)
                .secure(secureCookies)
                .sameSite(sameSite)
                .path("/")
                .maxAge(
                        Duration.ofSeconds(
                                jwtService.accessExpirationSeconds()
                        )
                )
                .build();

        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private void addRefreshCookie(
            String token,
            Instant expiresAt,
            HttpServletResponse response
    ) {
        long seconds = Math.max(
                0,
                Duration.between(Instant.now(), expiresAt).getSeconds()
        );

        ResponseCookie cookie = ResponseCookie
                .from(
                        JwtCookieAuthenticationFilter.REFRESH_COOKIE_NAME,
                        token
                )
                .httpOnly(true)
                .secure(secureCookies)
                .sameSite(sameSite)
                .path("/api/v1/auth")
                .maxAge(Duration.ofSeconds(seconds))
                .build();

        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private void clearWebCookies(HttpServletResponse response) {
        response.addHeader(
                HttpHeaders.SET_COOKIE,
                expiredCookie(
                        JwtCookieAuthenticationFilter.ACCESS_COOKIE_NAME,
                        "/"
                ).toString()
        );
        response.addHeader(
                HttpHeaders.SET_COOKIE,
                expiredCookie(
                        JwtCookieAuthenticationFilter.REFRESH_COOKIE_NAME,
                        "/api/v1/auth"
                ).toString()
        );
    }

    private ResponseCookie expiredCookie(String name, String path) {
        return ResponseCookie.from(name, "")
                .httpOnly(true)
                .secure(secureCookies)
                .sameSite(sameSite)
                .path(path)
                .maxAge(Duration.ZERO)
                .build();
    }

    private MobileTokenResponse mobileResponse(
            UserEntity user,
            CreatedSession session
    ) {
        return new MobileTokenResponse(
                "Bearer",
                jwtService.createAccessToken(user, session.session().getId()),
                jwtService.accessExpirationSeconds(),
                session.refreshToken(),
                session.session().getExpiresAt(),
                userMapper.toMeResponse(user)
        );
    }

    private MobileTokenResponse mobileResponse(
            UserEntity user,
            RotatedSession session
    ) {
        return new MobileTokenResponse(
                "Bearer",
                jwtService.createAccessToken(user, session.session().getId()),
                jwtService.accessExpirationSeconds(),
                session.refreshToken(),
                session.session().getExpiresAt(),
                userMapper.toMeResponse(user)
        );
    }

    private static SessionResponse sessionResponse(
            AuthSessionEntity session
    ) {
        return new SessionResponse(
                session.getId(),
                session.getClientType(),
                session.getDeviceName(),
                session.getUserAgent(),
                session.getIpAddress(),
                session.getCreatedAt(),
                session.getExpiresAt(),
                session.getLastUsedAt(),
                session.getRevokedAt(),
                session.isUsable()
        );
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
