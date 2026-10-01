package com.freezify.auth.internal;

import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties("freezify.cors")
@Validated
public record CorsProperties(@NotNull List<String> allowedOrigins) {}
