package app.financecopilot.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record RegisterRequest(
            @NotBlank @Email @Size(max = 255) String email,
            @NotBlank @Size(min = 8, max = 72, message = "must be 8-72 characters") String password,
            @Size(max = 100) String displayName) {
    }

    public record LoginRequest(
            @NotBlank @Email String email,
            @NotBlank String password) {
    }

    public record UserResponse(Long id, String email, String displayName) {
    }

    public record AuthResponse(String accessToken, String tokenType, long expiresIn, UserResponse user) {
        public static AuthResponse bearer(String token, long expiresIn, UserResponse user) {
            return new AuthResponse(token, "Bearer", expiresIn, user);
        }
    }
}
