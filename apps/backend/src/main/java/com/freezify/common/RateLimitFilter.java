package com.freezify.common;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Per-client-address limit on sensitive endpoints (credentials, invitation codes).
 *
 * <p>State lives in memory, which is only correct while a single instance is deployed
 * (see ARCHITECTURE.md, D14).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimitProperties properties;
    private final ProblemWriter problemWriter;
    private final Cache<String, Bucket> buckets;

    public RateLimitFilter(RateLimitProperties properties, ProblemWriter problemWriter) {
        this.properties = properties;
        this.problemWriter = problemWriter;
        // An idle bucket is full again after one period, so dropping it then loses nothing.
        this.buckets = Caffeine.newBuilder()
                .expireAfterAccess(properties.period().multipliedBy(2))
                .maximumSize(100_000)
                .build();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return properties.paths().stream().noneMatch(path::startsWith);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Bucket bucket = buckets.get(request.getRemoteAddr(), key -> newBucket());
        if (bucket.tryConsume(1)) {
            chain.doFilter(request, response);
            return;
        }
        response.setHeader("Retry-After", String.valueOf(properties.period().toSeconds()));
        problemWriter.write(
                response, HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED", "Too many requests. Try again later.");
    }

    private Bucket newBucket() {
        return Bucket.builder()
                .addLimit(limit -> limit.capacity(properties.capacity())
                        .refillGreedy(properties.capacity(), properties.period()))
                .build();
    }
}
