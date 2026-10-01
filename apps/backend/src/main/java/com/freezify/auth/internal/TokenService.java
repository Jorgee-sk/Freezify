package com.freezify.auth.internal;

import com.freezify.common.ApiException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Issues short-lived access JWTs and opaque, single-use refresh tokens.
 *
 * <p>Only the SHA-256 of a refresh token is stored. Each refresh rotates the token; presenting an already
 * rotated token means it was copied, so the whole family is revoked.
 */
@Service
public class TokenService {

    private static final Logger log = LoggerFactory.getLogger(TokenService.class);
    private static final int REFRESH_TOKEN_BYTES = 32;

    private final RefreshTokenRepository refreshTokens;
    private final JwtEncoder jwtEncoder;
    private final AuthProperties properties;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    TokenService(RefreshTokenRepository refreshTokens, JwtEncoder jwtEncoder, AuthProperties properties, Clock clock) {
        this.refreshTokens = refreshTokens;
        this.jwtEncoder = jwtEncoder;
        this.properties = properties;
        this.clock = clock;
    }

    public record IssuedTokens(UUID userId, String accessToken, String refreshToken, long expiresInSeconds) {}

    @Transactional
    public IssuedTokens issueFor(UUID userId) {
        return issue(userId, UUID.randomUUID());
    }

    // The family revocation must be committed even though the request fails.
    @Transactional(noRollbackFor = ApiException.class)
    public IssuedTokens rotate(String rawRefreshToken) {
        Instant now = clock.instant();
        RefreshTokenEntity current =
                refreshTokens.findByTokenHash(hash(rawRefreshToken)).orElseThrow(TokenService::invalidRefreshToken);
        if (current.isRevoked()) {
            int revoked = refreshTokens.revokeFamily(current.familyId(), now);
            log.warn("Refresh token reuse detected for user {}; revoked {} active token(s)", current.userId(), revoked);
            throw invalidRefreshToken();
        }
        if (current.isExpired(now)) {
            throw invalidRefreshToken();
        }
        current.revoke(now);
        return issue(current.userId(), current.familyId());
    }

    /** Idempotent: unknown or already revoked tokens are ignored. */
    @Transactional
    public void revoke(String rawRefreshToken) {
        refreshTokens.findByTokenHash(hash(rawRefreshToken)).ifPresent(token -> token.revoke(clock.instant()));
    }

    @Transactional
    public int deleteExpired() {
        return refreshTokens.deleteExpiredBefore(clock.instant());
    }

    private IssuedTokens issue(UUID userId, UUID familyId) {
        Instant now = clock.instant();

        byte[] bytes = new byte[REFRESH_TOKEN_BYTES];
        random.nextBytes(bytes);
        String refreshToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        refreshTokens.save(new RefreshTokenEntity(
                userId, familyId, hash(refreshToken), now, now.plus(properties.refreshTokenTtl())));

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .subject(userId.toString())
                .issuedAt(now)
                .expiresAt(now.plus(properties.accessTokenTtl()))
                .id(UUID.randomUUID().toString())
                .build();
        String accessToken = jwtEncoder
                .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();

        return new IssuedTokens(userId, accessToken, refreshToken, properties.accessTokenTtl().toSeconds());
    }

    static String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by the Java platform", e);
        }
    }

    private static ApiException invalidRefreshToken() {
        return ApiException.unauthorized("INVALID_REFRESH_TOKEN", "The refresh token is invalid or has expired.");
    }
}
