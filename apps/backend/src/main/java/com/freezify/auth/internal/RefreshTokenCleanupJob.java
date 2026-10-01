package com.freezify.auth.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration
@EnableScheduling
class RefreshTokenCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenCleanupJob.class);

    private final TokenService tokenService;

    RefreshTokenCleanupJob(TokenService tokenService) {
        this.tokenService = tokenService;
    }

    @Scheduled(cron = "0 30 3 * * *", zone = "UTC")
    void deleteExpiredRefreshTokens() {
        int deleted = tokenService.deleteExpired();
        log.info("Deleted {} expired refresh token(s)", deleted);
    }
}
