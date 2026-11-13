package app.financecopilot.controller;

import app.financecopilot.dto.AuthDtos.AuthResponse;
import app.financecopilot.dto.AuthDtos.LoginRequest;
import app.financecopilot.dto.AuthDtos.RegisterRequest;
import app.financecopilot.dto.AuthDtos.UserResponse;
import app.financecopilot.security.AuthenticatedUser;
import app.financecopilot.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create an account and receive a JWT")
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/login")
    @Operation(summary = "Exchange credentials for a JWT")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @GetMapping("/me")
    @Operation(summary = "Current user (used by the frontend to restore a session on reload)")
    public UserResponse me(@AuthenticationPrincipal AuthenticatedUser user) {
        return authService.me(user.id());
    }
}
