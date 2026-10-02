package com.freezify.notifications.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** An app installation that asked to receive push notifications for a user. */
@Entity
@Table(name = "device_tokens")
class DeviceTokenEntity {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, updatable = false)
    private String token;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Devices.Platform platform;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    protected DeviceTokenEntity() {}

    DeviceTokenEntity(String token, Instant now) {
        this.token = token;
        this.createdAt = now;
    }

    /** The installation now belongs to this user, whoever had it before. */
    DeviceTokenEntity seen(UUID userId, Devices.Platform platform, Instant now) {
        this.userId = userId;
        this.platform = platform;
        this.lastSeenAt = now;
        return this;
    }

    UUID userId() {
        return userId;
    }

    String token() {
        return token;
    }
}

interface DeviceTokenRepository extends JpaRepository<DeviceTokenEntity, UUID> {

    Optional<DeviceTokenEntity> findByToken(String token);

    List<DeviceTokenEntity> findByUserId(UUID userId);
}

/** The app installations of each user. */
@Service
public class Devices {

    public enum Platform {
        ANDROID,
        IOS,
        WEB
    }

    private final DeviceTokenRepository tokens;
    private final Clock clock;

    Devices(DeviceTokenRepository tokens, Clock clock) {
        this.tokens = tokens;
        this.clock = clock;
    }

    /**
     * Called by the app every time it starts signed in, and when the push service replaces its token. If
     * someone else was signed in on that installation before, it stops being theirs: a phone must never show
     * the food of the previous user.
     */
    @Transactional
    public void register(UUID userId, String token, Platform platform) {
        Instant now = clock.instant();
        tokens.save(tokens.findByToken(token)
                .orElseGet(() -> new DeviceTokenEntity(token, now))
                .seen(userId, platform, now));
    }

    /** Called when the user signs out. Only the owner of an installation can remove it. */
    @Transactional
    public void unregister(UUID userId, String token) {
        tokens.findByToken(token)
                .filter(device -> device.userId().equals(userId))
                .ifPresent(tokens::delete);
    }

    @Transactional(readOnly = true)
    List<String> tokensOf(UUID userId) {
        return tokens.findByUserId(userId).stream().map(DeviceTokenEntity::token).toList();
    }

    /**
     * Forgets an installation the push service reports as gone. It is called after the transaction that created
     * the notification has committed, so it needs one of its own to be saved at all.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void forget(String token) {
        tokens.findByToken(token).ifPresent(tokens::delete);
    }
}
