package com.freezify.users;

import java.time.Instant;
import java.util.UUID;

public record UserAccount(UUID id, String email, String displayName, String locale, Instant createdAt) {}
