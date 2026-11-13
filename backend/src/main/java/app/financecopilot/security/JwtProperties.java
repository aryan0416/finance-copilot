package app.financecopilot.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(String secret, Duration expiration) {

    private static final int MIN_SECRET_BYTES = 32; // HS256 needs >= 256 bits

    public JwtProperties {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("JWT_SECRET must be set and at least 32 bytes long");
        }
        if (expiration == null) {
            expiration = Duration.ofHours(24);
        }
    }
}
