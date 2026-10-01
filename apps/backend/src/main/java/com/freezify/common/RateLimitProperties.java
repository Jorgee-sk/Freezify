package com.freezify.common;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * @param capacity requests allowed per client and period
 * @param period   window after which the allowance is fully refilled
 * @param paths    path prefixes the limit applies to
 */
@ConfigurationProperties("freezify.rate-limit")
@Validated
public record RateLimitProperties(@Min(1) int capacity, @NotNull Duration period, @NotNull List<String> paths) {}
