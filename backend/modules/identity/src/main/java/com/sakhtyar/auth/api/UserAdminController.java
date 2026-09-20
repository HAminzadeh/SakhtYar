package com.sakhtyar.auth.api;

import com.sakhtyar.auth.api.UserAdminDtos.AdminResetPasswordRequest;
import com.sakhtyar.auth.api.UserAdminDtos.CreateUserRequest;
import com.sakhtyar.auth.api.UserAdminDtos.UpdateUserRequest;
import com.sakhtyar.auth.api.UserAdminDtos.UserResponse;
import com.sakhtyar.identity.application.UserAdminService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/users")
public class UserAdminController {

    private final UserAdminService service;

    public UserAdminController(UserAdminService service) {
        this.service = service;
    }

    @GetMapping
    public List<UserResponse> list() {
        return service.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse create(
            @Valid @RequestBody CreateUserRequest request
    ) {
        return service.create(request);
    }

    @PutMapping("/{userId}")
    public UserResponse update(
            @PathVariable UUID userId,
            @Valid @RequestBody UpdateUserRequest request,
            Authentication authentication
    ) {
        return service.update(
                userId,
                request,
                authentication.getName()
        );
    }

    @PostMapping("/{userId}/unlock")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unlock(@PathVariable UUID userId) {
        service.unlock(userId);
    }

    @PostMapping("/{userId}/reset-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetPassword(
            @PathVariable UUID userId,
            @Valid @RequestBody AdminResetPasswordRequest request
    ) {
        service.resetPassword(userId, request.newPassword());
    }
}
