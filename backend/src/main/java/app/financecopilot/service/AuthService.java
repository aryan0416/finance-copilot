package app.financecopilot.service;

import app.financecopilot.domain.User;
import app.financecopilot.dto.AuthDtos.AuthResponse;
import app.financecopilot.dto.AuthDtos.LoginRequest;
import app.financecopilot.dto.AuthDtos.RegisterRequest;
import app.financecopilot.dto.AuthDtos.UserResponse;
import app.financecopilot.exception.ApiException;
import app.financecopilot.repository.UserRepository;
import app.financecopilot.security.JwtService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class AuthService {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository users, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        if (users.existsByEmailIgnoreCase(email)) {
            throw ApiException.conflict("An account with this email already exists");
        }
        User user = users.save(new User(email, passwordEncoder.encode(request.password()), request.displayName()));
        return tokenFor(user);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        User user = users.findByEmailIgnoreCase(normalizeEmail(request.email()))
                .filter(u -> passwordEncoder.matches(request.password(), u.getPasswordHash()))
                // Same message for unknown email and wrong password, so accounts can't be enumerated.
                .orElseThrow(() -> ApiException.unauthorized("Invalid email or password"));
        return tokenFor(user);
    }

    @Transactional(readOnly = true)
    public UserResponse me(Long userId) {
        return users.findById(userId).map(AuthService::toResponse)
                .orElseThrow(() -> ApiException.notFound("User"));
    }

    private AuthResponse tokenFor(User user) {
        String token = jwtService.issueToken(user.getId(), user.getEmail());
        return AuthResponse.bearer(token, jwtService.expiresInSeconds(), toResponse(user));
    }

    private static UserResponse toResponse(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getDisplayName());
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
