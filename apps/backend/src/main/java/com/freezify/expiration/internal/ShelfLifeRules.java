package com.freezify.expiration.internal;

import com.freezify.expiration.ExpirationEstimator;
import com.freezify.expiration.ShelfLife;
import com.freezify.food.FoodCategory;
import com.freezify.food.StorageLocation;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.hibernate.annotations.Immutable;
import org.jspecify.annotations.Nullable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;

/** Rules are created by migrations only. A rule is either for one catalog food or for a whole category. */
@Entity
@Immutable
@Table(name = "shelf_life_rules")
class ShelfLifeRuleEntity {

    @Id
    private UUID id;

    @Column(name = "food_id")
    private @Nullable UUID foodId;

    @Enumerated(EnumType.STRING)
    private @Nullable FoodCategory category;

    @Enumerated(EnumType.STRING)
    @Column(name = "storage_location", nullable = false)
    private StorageLocation storageLocation;

    @Column(name = "unopened_days")
    private @Nullable Integer unopenedDays;

    @Column(name = "opened_days")
    private @Nullable Integer openedDays;

    protected ShelfLifeRuleEntity() {}

    @Nullable UUID foodId() {
        return foodId;
    }

    @Nullable FoodCategory category() {
        return category;
    }

    StorageLocation storageLocation() {
        return storageLocation;
    }

    ShelfLife shelfLife() {
        return new ShelfLife(unopenedDays, openedDays);
    }
}

interface ShelfLifeRuleRepository extends JpaRepository<ShelfLifeRuleEntity, UUID> {}

/**
 * Like the food catalog, the rules are few and only change with a deployment, so they are read once and
 * looked up in memory.
 */
@Service
class ShelfLifeRules implements ExpirationEstimator {

    private record FoodKey(UUID foodId, StorageLocation storageLocation) {}

    private record CategoryKey(FoodCategory category, StorageLocation storageLocation) {}

    private record Loaded(Map<FoodKey, ShelfLife> byFood, Map<CategoryKey, ShelfLife> byCategory) {}

    private final ShelfLifeRuleRepository repository;
    private volatile @Nullable Loaded loaded;

    ShelfLifeRules(ShelfLifeRuleRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<Expiration> resolve(Input input) {
        return ShelfLife.resolve(input, ruleFor(input));
    }

    /** The rule of the food itself when there is one; otherwise the rule of its category. */
    private @Nullable ShelfLife ruleFor(Input input) {
        Loaded rules = rules();
        if (input.foodId() != null) {
            ShelfLife specific = rules.byFood().get(new FoodKey(input.foodId(), input.storageLocation()));
            if (specific != null) {
                return specific;
            }
        }
        return rules.byCategory().get(new CategoryKey(input.category(), input.storageLocation()));
    }

    private Loaded rules() {
        Loaded current = loaded;
        if (current == null) {
            Map<FoodKey, ShelfLife> byFood = new HashMap<>();
            Map<CategoryKey, ShelfLife> byCategory = new HashMap<>();
            for (ShelfLifeRuleEntity rule : repository.findAll()) {
                if (rule.foodId() != null) {
                    byFood.put(new FoodKey(rule.foodId(), rule.storageLocation()), rule.shelfLife());
                } else if (rule.category() != null) {
                    byCategory.put(new CategoryKey(rule.category(), rule.storageLocation()), rule.shelfLife());
                }
            }
            current = new Loaded(Map.copyOf(byFood), Map.copyOf(byCategory));
            loaded = current;
        }
        return current;
    }
}
