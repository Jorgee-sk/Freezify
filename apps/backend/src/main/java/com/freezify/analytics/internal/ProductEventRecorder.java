package com.freezify.analytics.internal;

import com.freezify.inventory.InventoryEvents.FoodItemAdded;
import com.freezify.inventory.InventoryEvents.FoodItemConsumed;
import com.freezify.inventory.InventoryEvents.FoodItemDiscarded;
import com.freezify.mealplanning.MealPlanEvents.MealPlanCreated;
import com.freezify.notifications.NotificationEvents.NotificationOpened;
import com.freezify.recipes.RecipeEvents.RecipeCooked;
import com.freezify.recipes.RecipeEvents.RecipeViewed;
import com.freezify.users.UserRegistered;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

@Entity
@Table(name = "product_events")
class ProductEventEntity {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(nullable = false, updatable = false)
    private String name;

    @Column(name = "user_id", updatable = false)
    private @Nullable UUID userId;

    @Column(name = "household_id", updatable = false)
    private @Nullable UUID householdId;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    protected ProductEventEntity() {}

    ProductEventEntity(String name, @Nullable UUID userId, @Nullable UUID householdId, Instant occurredAt) {
        this.name = name;
        this.userId = userId;
        this.householdId = householdId;
        this.occurredAt = occurredAt;
    }
}

interface ProductEventRepository extends JpaRepository<ProductEventEntity, UUID> {}

/**
 * Turns what happens in the other modules into the product metrics events (activation, retention, waste
 * avoided). Recording happens after the originating transaction commits and can never make it fail.
 */
@Component
class ProductEventRecorder {

    private static final Logger log = LoggerFactory.getLogger(ProductEventRecorder.class);

    private final ProductEventRepository events;
    private final TransactionTemplate newTransaction;
    private final Clock clock;

    ProductEventRecorder(ProductEventRepository events, PlatformTransactionManager transactionManager, Clock clock) {
        this.events = events;
        this.newTransaction = new TransactionTemplate(transactionManager);
        this.newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.clock = clock;
    }

    @TransactionalEventListener
    void on(UserRegistered event) {
        record("user_registered", event.userId(), null);
    }

    @TransactionalEventListener
    void on(FoodItemAdded event) {
        record("food_added", event.userId(), event.householdId());
    }

    @TransactionalEventListener
    void on(FoodItemConsumed event) {
        record("food_consumed", event.userId(), event.householdId());
    }

    @TransactionalEventListener
    void on(FoodItemDiscarded event) {
        record("food_discarded", event.userId(), event.householdId());
    }

    @TransactionalEventListener
    void on(NotificationOpened event) {
        record("notification_opened", event.userId(), event.householdId());
    }

    @TransactionalEventListener
    void on(RecipeViewed event) {
        record("recipe_viewed", event.userId(), null);
    }

    @TransactionalEventListener
    void on(RecipeCooked event) {
        record("recipe_cooked", event.userId(), event.householdId());
    }

    @TransactionalEventListener
    void on(MealPlanCreated event) {
        record("meal_plan_created", event.userId(), event.householdId());
    }

    private void record(String name, @Nullable UUID userId, @Nullable UUID householdId) {
        try {
            newTransaction.executeWithoutResult(
                    status -> events.save(new ProductEventEntity(name, userId, householdId, clock.instant())));
        } catch (RuntimeException e) {
            log.error("Could not record product event {}", name, e);
        }
    }
}
