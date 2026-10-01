package com.freezify.users.internal;

import com.freezify.common.ApiException;
import com.freezify.users.UserAccount;
import com.freezify.users.Users;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService implements Users {

    private final UserRepository users;

    UserService(UserRepository users) {
        this.users = users;
    }

    @Override
    @Transactional
    public UserAccount create(NewUser newUser) {
        String email = normalizeEmail(newUser.email());
        if (users.existsByEmail(email)) {
            throw emailTaken();
        }
        try {
            UserEntity saved = users.saveAndFlush(new UserEntity(
                    email, newUser.passwordHash(), newUser.displayName().strip(), newUser.locale()));
            return saved.toAccount();
        } catch (DataIntegrityViolationException e) {
            // Two concurrent registrations with the same email: the unique constraint decides.
            throw emailTaken();
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UserAccount> findById(UUID id) {
        return users.findById(id).map(UserEntity::toAccount);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserAccount> findAllById(Collection<UUID> ids) {
        return users.findAllById(ids).stream().map(UserEntity::toAccount).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Credentials> findCredentialsByEmail(String email) {
        return users.findByEmail(normalizeEmail(email))
                .map(user -> new Credentials(user.toAccount(), user.passwordHash()));
    }

    @Transactional
    public UserAccount updateProfile(UUID id, @Nullable String displayName, @Nullable String locale) {
        UserEntity user = users.findById(id).orElseThrow(UserService::userNotFound);
        if (displayName != null) {
            user.rename(displayName.strip());
        }
        if (locale != null) {
            user.changeLocale(locale);
        }
        return users.saveAndFlush(user).toAccount();
    }

    @Transactional(readOnly = true)
    public UserAccount get(UUID id) {
        return findById(id).orElseThrow(UserService::userNotFound);
    }

    static String normalizeEmail(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }

    private static ApiException userNotFound() {
        // A valid token whose user no longer exists is treated as an invalid session.
        return ApiException.unauthorized("UNAUTHORIZED", "User no longer exists.");
    }

    private static ApiException emailTaken() {
        return ApiException.conflict("EMAIL_ALREADY_REGISTERED", "An account with this email already exists.");
    }
}
