package com.freezify.auth.internal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties("freezify.auth")
@Validated
public record AuthProperties(
        @NotBlank String jwtSecret,
        @NotBlank String issuer,
        @NotNull Duration accessTokenTtl,
        @NotNull Duration refreshTokenTtl) {

    private static final int MIN_SECRET_BYTES = 32;

    public AuthProperties {
        // HS256 needs a key of at least 256 bits; a shorter secret must stop the application from starting.
        if (jwtSecret != null && jwtSecret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalArgumentException(
                    "freezify.auth.jwt-secret (FREEZIFY_JWT_SECRET) must be at least " + MIN_SECRET_BYTES + " bytes");
        }
    }
}
