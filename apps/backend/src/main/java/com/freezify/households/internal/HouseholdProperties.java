package com.freezify.households.internal;

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties("freezify.households")
@Validated
public record HouseholdProperties(@NotNull Duration invitationTtl) {}
