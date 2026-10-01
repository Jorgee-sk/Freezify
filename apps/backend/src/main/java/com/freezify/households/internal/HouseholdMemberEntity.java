package com.freezify.households.internal;

import com.freezify.households.HouseholdRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "household_members")
class HouseholdMemberEntity {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(name = "household_id", nullable = false, updatable = false)
    private UUID householdId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private HouseholdRole role;

    @CreationTimestamp
    @Column(name = "joined_at", nullable = false, updatable = false)
    private Instant joinedAt;

    protected HouseholdMemberEntity() {}

    HouseholdMemberEntity(UUID householdId, UUID userId, HouseholdRole role) {
        this.householdId = householdId;
        this.userId = userId;
        this.role = role;
    }

    UUID householdId() {
        return householdId;
    }

    UUID userId() {
        return userId;
    }

    HouseholdRole role() {
        return role;
    }

    Instant joinedAt() {
        return joinedAt;
    }
}
