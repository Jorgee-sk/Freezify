package com.freezify.users;

import java.util.UUID;

/** Published when a new account is created. */
public record UserRegistered(UUID userId) {}
