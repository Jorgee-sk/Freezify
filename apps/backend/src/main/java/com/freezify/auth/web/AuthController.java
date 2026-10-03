package com.freezify.auth.web;

import com.freezify.auth.internal.AuthProperties;
import com.freezify.auth.internal.AuthService;
import com.freezify.auth.internal.AuthService.Session;
import com.freezify.auth.internal.TokenService;
import com.freezify.auth.internal.TokenService.IssuedTokens;
import com.freezify.common.ApiException;
import com.freezify.users.UserAccount;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Sessions. The mobile app keeps its refresh token in the device's secure storage and sends it in the body. A
 * browser asks for the cookie transport instead ({@value #SESSION_HEADER}: {@value #COOKIE_TRANSPORT}): the
 * refresh token then travels only in an {@code HttpOnly} cookie that scripts cannot read, so an XSS cannot steal
 * it. The custom header is also what protects the cookie from cross-site requests: a form cannot send it, and a
 * script from another origin needs a CORS preflight that is refused.
 */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Auth")
@SecurityRequirements
class AuthController {

    static final String SESSION_HEADER = "X-Freezify-Session";
    static final String COOKIE_TRANSPORT = "cookie";
    static final String REFRESH_COOKIE = "freezify_refresh";

    private static final String DEFAULT_LOCALE = "es";
    /** Sent only to the endpoints that use it. */
    private static final String COOKIE_PATH = "/api/v1/auth";

    private final AuthService authService;
    private final TokenService tokenService;
    private final AuthProperties properties;

    AuthController(AuthService authService, TokenService tokenService, AuthProperties properties) {
        this.authService = authService;
        this.tokenService = tokenService;
        this.properties = properties;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    SessionResponse register(
            @Valid @RequestBody RegisterRequest request,
            @RequestHeader(name = SESSION_HEADER, required = false) @Nullable String transport,
            HttpServletResponse response) {
        String locale = request.locale() == null ? DEFAULT_LOCALE : request.locale();
        Session session = authService.register(request.email(), request.password(), request.displayName(), locale);
        return SessionResponse.of(session, deliver(session.tokens(), transport, response));
    }

    @PostMapping("/login")
    SessionResponse login(
            @Valid @RequestBody LoginRequest request,
            @RequestHeader(name = SESSION_HEADER, required = false) @Nullable String transport,
            HttpServletResponse response) {
        Session session = authService.login(request.email(), request.password());
        return SessionResponse.of(session, deliver(session.tokens(), transport, response));
    }

    @PostMapping("/refresh")
    TokenResponse refresh(
            @Valid @RequestBody(required = false) @Nullable RefreshRequest request,
            @RequestHeader(name = SESSION_HEADER, required = false) @Nullable String transport,
            @CookieValue(name = REFRESH_COOKIE, required = false) @Nullable String cookie,
            HttpServletResponse response) {
        String presented = presented(request, transport, cookie);
        IssuedTokens tokens;
        try {
            tokens = tokenService.rotate(presented);
        } catch (ApiException e) {
            // A cookie that no longer works is of no use to the browser: it is removed.
            if (usesCookie(transport)) {
                clearCookie(response);
            }
            throw e;
        }
        return TokenResponse.of(tokens, deliver(tokens, transport, response));
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void logout(
            @Valid @RequestBody(required = false) @Nullable RefreshRequest request,
            @RequestHeader(name = SESSION_HEADER, required = false) @Nullable String transport,
            @CookieValue(name = REFRESH_COOKIE, required = false) @Nullable String cookie,
            HttpServletResponse response) {
        String token = request != null && request.refreshToken() != null
                ? request.refreshToken()
                : usesCookie(transport) ? cookie : null;
        if (token != null) {
            tokenService.revoke(token);
        }
        if (usesCookie(transport)) {
            clearCookie(response);
        }
    }

    /** The token from the body, or, for a browser that asked for the cookie transport, from the cookie. */
    private static String presented(
            @Nullable RefreshRequest request, @Nullable String transport, @Nullable String cookie) {
        if (request != null && request.refreshToken() != null && !request.refreshToken().isBlank()) {
            return request.refreshToken();
        }
        if (usesCookie(transport) && cookie != null && !cookie.isBlank()) {
            return cookie;
        }
        throw ApiException.unauthorized("INVALID_REFRESH_TOKEN", "The refresh token is invalid or has expired.");
    }

    /**
     * Hands the refresh token over the transport the client asked for.
     *
     * @return what goes in the body: the token, or {@code null} when it went into the cookie
     */
    private @Nullable String deliver(IssuedTokens tokens, @Nullable String transport, HttpServletResponse response) {
        if (!usesCookie(transport)) {
            return tokens.refreshToken();
        }
        response.addHeader(
                HttpHeaders.SET_COOKIE, cookie(tokens.refreshToken(), properties.refreshTokenTtl()).toString());
        return null;
    }

    private void clearCookie(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, cookie("", Duration.ZERO).toString());
    }

    private ResponseCookie cookie(String value, Duration maxAge) {
        return ResponseCookie.from(REFRESH_COOKIE, value)
                .httpOnly(true)
                .secure(properties.refreshCookieSecure())
                .sameSite("Strict")
                .path(COOKIE_PATH)
                .maxAge(maxAge)
                .build();
    }

    private static boolean usesCookie(@Nullable String transport) {
        return COOKIE_TRANSPORT.equalsIgnoreCase(transport);
    }

    record RegisterRequest(
            @NotBlank @Email @Size(max = 320) String email,
            @NotBlank @Size(min = 8, max = 72) String password,
            @NotBlank @Size(max = 80) String displayName,
            @Nullable @Pattern(regexp = "es|en", message = "must be 'es' or 'en'") String locale) {}

    record LoginRequest(@NotBlank @Size(max = 320) String email, @NotBlank @Size(max = 72) String password) {}

    /**
     * @param refreshToken absent for a browser using the cookie transport
     */
    record RefreshRequest(@Nullable @Size(max = 200) String refreshToken) {}

    /**
     * @param refreshToken {@code null} when it went into the cookie
     */
    record TokenResponse(String accessToken, @Nullable String refreshToken, long expiresIn) {
        static TokenResponse of(IssuedTokens tokens, @Nullable String refreshToken) {
            return new TokenResponse(tokens.accessToken(), refreshToken, tokens.expiresInSeconds());
        }
    }

    /**
     * @param refreshToken {@code null} when it went into the cookie
     */
    record SessionResponse(String accessToken, @Nullable String refreshToken, long expiresIn, UserAccount user) {
        static SessionResponse of(Session session, @Nullable String refreshToken) {
            IssuedTokens tokens = session.tokens();
            return new SessionResponse(tokens.accessToken(), refreshToken, tokens.expiresInSeconds(), session.user());
        }
    }
}
