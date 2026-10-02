package com.freezify.recipes.internal;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Every recipe, read once. Like the food catalog, recipes only change with a deployment (they are seeded by
 * migrations), and scoring needs all of them at hand for every request.
 */
@Component
class RecipeBook {

    private record Loaded(List<Recipe> all, Map<UUID, Recipe> byId) {}

    private final RecipeRepository repository;
    private final TransactionTemplate readOnly;
    private volatile Loaded loaded;

    RecipeBook(RecipeRepository repository, PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.readOnly = new TransactionTemplate(transactionManager);
        this.readOnly.setReadOnly(true);
    }

    /** In the order of their Spanish name. */
    List<Recipe> all() {
        return book().all();
    }

    Optional<Recipe> find(UUID id) {
        return Optional.ofNullable(book().byId().get(id));
    }

    private Loaded book() {
        Loaded current = loaded;
        if (current == null) {
            // Ingredients are read lazily, so recipes are turned into plain values while the transaction is open.
            List<Recipe> recipes = readOnly.execute(status -> repository.findAll().stream()
                    .map(RecipeEntity::toRecipe)
                    .sorted(Comparator.comparing(Recipe::nameEs))
                    .toList());
            current = new Loaded(recipes, recipes.stream().collect(Collectors.toMap(Recipe::id, Function.identity())));
            loaded = current;
        }
        return current;
    }
}
