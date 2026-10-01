package com.freezify.auth.web;

import com.freezify.auth.internal.AuthService;
import com.freezify.auth.internal.AuthService.Session;
import com.freezify.auth.internal.TokenService;
import com.freezify.auth.internal.TokenService.IssuedTokens;
import com.freezify.users.UserAccount;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Auth")
@SecurityRequirements
class AuthController {

    private static final String DEFAULT_LOCALE = "es";

    private final AuthService authService;
    private final TokenService tokenService;

    AuthController(AuthService authService, TokenService tokenService) {
        this.authService = authService;
        this.tokenService = tokenService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    SessionResponse register(@Valid @RequestBody RegisterRequest request) {
        String locale = request.locale() == null ? DEFAULT_LOCALE : request.locale();
        return SessionResponse.of(
                authService.register(request.email(), request.password(), request.displayName(), locale));
    }

    @PostMapping("/login")
    SessionResponse login(@Valid @RequestBody LoginRequest request) {
        return SessionResponse.of(authService.login(request.email(), request.password()));
    }

    @PostMapping("/refresh")
    TokenResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return TokenResponse.of(tokenService.rotate(request.refreshToken()));
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void logout(@Valid @RequestBody RefreshRequest request) {
        tokenService.revoke(request.refreshToken());
    }

    record RegisterRequest(
            @NotBlank @Email @Size(max = 320) String email,
            @NotBlank @Size(min = 8, max = 72) String password,
            @NotBlank @Size(max = 80) String displayName,
            @Nullable @Pattern(regexp = "es|en", message = "must be 'es' or 'en'") String locale) {}

    record LoginRequest(@NotBlank @Size(max = 320) String email, @NotBlank @Size(max = 72) String password) {}

    record RefreshRequest(@NotBlank @Size(max = 200) String refreshToken) {}

    record TokenResponse(String accessToken, String refreshToken, long expiresIn) {
        static TokenResponse of(IssuedTokens tokens) {
            return new TokenResponse(tokens.accessToken(), tokens.refreshToken(), tokens.expiresInSeconds());
        }
    }

    record SessionResponse(String accessToken, String refreshToken, long expiresIn, UserAccount user) {
        static SessionResponse of(Session session) {
            IssuedTokens tokens = session.tokens();
            return new SessionResponse(
                    tokens.accessToken(), tokens.refreshToken(), tokens.expiresInSeconds(), session.user());
        }
    }
}
