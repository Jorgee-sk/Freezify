package com.freezify.users;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** What other modules may ask the users module. */
public interface Users {

    /**
     * @throws com.freezify.common.ApiException 409 {@code EMAIL_ALREADY_REGISTERED}
     */
    UserAccount create(NewUser newUser);

    Optional<UserAccount> findById(UUID id);

    List<UserAccount> findAllById(Collection<UUID> ids);

    /** Only for authentication: exposes the stored password hash. */
    Optional<Credentials> findCredentialsByEmail(String email);

    record NewUser(String email, String passwordHash, String displayName, String locale) {}

    record Credentials(UserAccount account, String passwordHash) {}
}
