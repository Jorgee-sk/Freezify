package com.freezify.auth.internal;

import com.freezify.auth.internal.TokenService.IssuedTokens;
import com.freezify.common.ApiException;
import com.freezify.users.UserAccount;
import com.freezify.users.Users;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    // bcrypt only reads the first 72 bytes; longer passwords are rejected instead of being silently truncated.
    private static final int MAX_PASSWORD_BYTES = 72;

    private final Users users;
    private final TokenService tokens;
    private final PasswordEncoder passwordEncoder;
    private final String dummyHash;

    AuthService(Users users, TokenService tokens, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.tokens = tokens;
        this.passwordEncoder = passwordEncoder;
        this.dummyHash = passwordEncoder.encode("freezify-timing-equalizer");
    }

    public record Session(IssuedTokens tokens, UserAccount user) {}

    @Transactional
    public Session register(String email, String password, String displayName, String locale) {
        requireUsablePassword(password);
        UserAccount user = users.create(new Users.NewUser(email, passwordEncoder.encode(password), displayName, locale));
        return new Session(tokens.issueFor(user.id()), user);
    }

    @Transactional
    public Session login(String email, String password) {
        Optional<Users.Credentials> credentials = users.findCredentialsByEmail(email);
        // Hash even when the account does not exist, so response time does not reveal which emails are registered.
        String hash = credentials.map(Users.Credentials::passwordHash).orElse(dummyHash);
        boolean matches = fitsBcrypt(password) && passwordEncoder.matches(password, hash);
        if (credentials.isEmpty() || !matches) {
            throw ApiException.unauthorized("INVALID_CREDENTIALS", "Email or password is incorrect.");
        }
        UserAccount user = credentials.get().account();
        return new Session(tokens.issueFor(user.id()), user);
    }

    private static void requireUsablePassword(String password) {
        if (!fitsBcrypt(password)) {
            throw ApiException.badRequest("PASSWORD_TOO_LONG", "Password must be at most 72 bytes long.");
        }
    }

    private static boolean fitsBcrypt(String password) {
        return password.getBytes(StandardCharsets.UTF_8).length <= MAX_PASSWORD_BYTES;
    }
}
