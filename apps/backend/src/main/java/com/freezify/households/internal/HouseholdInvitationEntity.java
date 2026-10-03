package com.freezify.households.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "household_invitations")
class HouseholdInvitationEntity {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(name = "household_id", nullable = false, updatable = false)
    private UUID householdId;

    @Column(nullable = false, updatable = false)
    private String code;

    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected HouseholdInvitationEntity() {}

    HouseholdInvitationEntity(UUID householdId, String code, UUID createdBy, Instant createdAt, Instant expiresAt) {
        this.householdId = householdId;
        this.code = code;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    UUID householdId() {
        return householdId;
    }

    String code() {
        return code;
    }

    Instant expiresAt() {
        return expiresAt;
    }

    UUID createdBy() {
        return createdBy;
    }

    boolean isExpired(Instant now) {
        return !expiresAt.isAfter(now);
    }
}
