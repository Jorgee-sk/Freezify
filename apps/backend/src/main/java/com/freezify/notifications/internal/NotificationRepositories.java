package com.freezify.notifications.internal;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface NotificationRepository extends JpaRepository<NotificationEntity, UUID> {

    Page<NotificationEntity> findByUserId(UUID userId, Pageable pageable);

    Optional<NotificationEntity> findByIdAndUserId(UUID id, UUID userId);

    long countByUserIdAndReadAtIsNull(UUID userId);

    Optional<NotificationEntity> findFirstByUserIdAndHouseholdIdAndTypeOrderByDayDesc(
            UUID userId, UUID householdId, NotificationType type);

    List<NotificationEntity> findByUserIdAndHouseholdId(UUID userId, UUID householdId);

    @Modifying
    @Query("update NotificationEntity n set n.readAt = :now where n.userId = :userId and n.readAt is null")
    void markAllRead(@Param("userId") UUID userId, @Param("now") Instant now);
}

interface NotificationPreferencesRepository extends JpaRepository<NotificationPreferencesEntity, UUID> {}

interface ItemAlertRepository extends JpaRepository<ItemAlertEntity, UUID> {

    List<ItemAlertEntity> findByUserIdAndHouseholdId(UUID userId, UUID householdId);
}

interface NotificationCheckRepository extends JpaRepository<NotificationCheckEntity, UUID> {}
