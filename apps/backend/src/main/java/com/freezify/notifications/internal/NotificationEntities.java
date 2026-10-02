package com.freezify.notifications.internal;

import com.freezify.expiration.ExpirationPriority;
import com.freezify.food.FoodCategory;
import com.freezify.notifications.internal.Preferences.Frequency;
import com.freezify.notifications.internal.Preferences.Threshold;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Converter;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.UuidGenerator;
import org.jspecify.annotations.Nullable;

@Entity
@Table(name = "notifications")
class NotificationEntity {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "household_id", nullable = false, updatable = false)
    private UUID householdId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private NotificationType type;

    @Column(nullable = false, updatable = false)
    private LocalDate day;

    @Column(name = "item_count", nullable = false, updatable = false)
    private int itemCount;

    @ElementCollection
    @CollectionTable(name = "notification_items", joinColumns = @JoinColumn(name = "notification_id"))
    @OrderColumn(name = "position")
    @BatchSize(size = 50)
    private List<NotifiedItem> items = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "read_at")
    private @Nullable Instant readAt;

    protected NotificationEntity() {}

    NotificationEntity(
            UUID userId,
            UUID householdId,
            NotificationType type,
            LocalDate day,
            int itemCount,
            List<NotifiedItem> items,
            Instant createdAt) {
        this.userId = userId;
        this.householdId = householdId;
        this.type = type;
        this.day = day;
        this.itemCount = itemCount;
        this.items.addAll(items);
        this.createdAt = createdAt;
    }

    /**
     * @return whether this was the first time
     */
    boolean markRead(Instant now) {
        if (readAt != null) {
            return false;
        }
        readAt = now;
        return true;
    }

    UUID id() {
        return id;
    }

    UUID householdId() {
        return householdId;
    }

    LocalDate day() {
        return day;
    }

    NotificationView toView(String householdName) {
        return new NotificationView(
                id,
                type,
                householdId,
                householdName,
                day,
                itemCount,
                items.stream()
                        .map(item -> new NotificationView.Item(
                                item.name(),
                                item.expirationDate(),
                                item.estimated(),
                                ExpirationPriority.daysLeft(item.expirationDate(), day)))
                        .toList(),
                createdAt,
                readAt != null);
    }
}

@Embeddable
record NotifiedItem(
        @Column(nullable = false) String name,
        @Column(name = "expiration_date", nullable = false) LocalDate expirationDate,
        @Column(nullable = false) boolean estimated) {}

@Entity
@Table(name = "notification_preferences")
class NotificationPreferencesEntity {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "expiration_alerts", nullable = false)
    private boolean expirationAlerts;

    @Column(name = "delivery_hour", nullable = false)
    private int deliveryHour;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Frequency frequency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Threshold threshold;

    @Convert(converter = CategorySetConverter.class)
    @Column(name = "muted_categories", nullable = false)
    private Set<FoodCategory> mutedCategories;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected NotificationPreferencesEntity() {}

    NotificationPreferencesEntity(UUID userId) {
        this.userId = userId;
    }

    NotificationPreferencesEntity apply(Preferences preferences, Instant now) {
        this.expirationAlerts = preferences.expirationAlerts();
        this.deliveryHour = preferences.deliveryHour();
        this.frequency = preferences.frequency();
        this.threshold = preferences.threshold();
        this.mutedCategories = preferences.mutedCategories();
        this.updatedAt = now;
        return this;
    }

    Preferences toPreferences() {
        return new Preferences(expirationAlerts, deliveryHour, frequency, threshold, mutedCategories);
    }
}

/** Stores the categories as their names separated by commas, in a stable order. */
@Converter
class CategorySetConverter implements AttributeConverter<Set<FoodCategory>, String> {

    @Override
    public String convertToDatabaseColumn(Set<FoodCategory> categories) {
        return categories.stream().sorted().map(Enum::name).collect(Collectors.joining(","));
    }

    @Override
    public Set<FoodCategory> convertToEntityAttribute(String stored) {
        Set<FoodCategory> categories = EnumSet.noneOf(FoodCategory.class);
        Arrays.stream(stored.split(","))
                .filter(name -> !name.isBlank())
                .map(FoodCategory::valueOf)
                .forEach(categories::add);
        return categories;
    }
}

/** The most pressing level a user has already been told about for an item. */
@Entity
@Table(name = "notification_item_alerts")
class ItemAlertEntity {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "household_id", nullable = false, updatable = false)
    private UUID householdId;

    @Column(name = "item_id", nullable = false, updatable = false)
    private UUID itemId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ExpirationPriority priority;

    protected ItemAlertEntity() {}

    ItemAlertEntity(UUID userId, UUID householdId, UUID itemId, ExpirationPriority priority) {
        this.userId = userId;
        this.householdId = householdId;
        this.itemId = itemId;
        this.priority = priority;
    }

    UUID itemId() {
        return itemId;
    }

    /**
     * Records the level the item is at now.
     *
     * @return whether it is more pressing than what the user was last told
     */
    boolean moveTo(ExpirationPriority current) {
        boolean morePressing = current.compareTo(priority) < 0;
        priority = current;
        return morePressing;
    }
}

@Entity
@Table(name = "notification_checks")
class NotificationCheckEntity {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "checked_on", nullable = false)
    private LocalDate checkedOn;

    protected NotificationCheckEntity() {}

    NotificationCheckEntity(UUID userId) {
        this.userId = userId;
    }

    boolean doneOn(LocalDate day) {
        return checkedOn != null && !checkedOn.isBefore(day);
    }

    NotificationCheckEntity on(LocalDate day) {
        this.checkedOn = day;
        return this;
    }
}
