package com.freezify.food.internal;

import com.freezify.food.Food;
import com.freezify.food.FoodCatalog;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;

interface FoodRepository extends JpaRepository<FoodEntity, UUID> {}

/**
 * The catalog is small and only changes with a deployment (it is seeded by migrations), so it is read once
 * and searched in memory. That keeps accent-insensitive matching out of SQL.
 */
@Service
class FoodCatalogService implements FoodCatalog {

    private record Indexed(Food food, String es, String en) {
        String name(String language) {
            return "es".equals(language) ? es : en;
        }
    }

    private final FoodRepository repository;
    private volatile Loaded loaded;

    private record Loaded(Map<UUID, Food> byId, List<Indexed> index) {}

    FoodCatalogService(FoodRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<Food> findById(UUID id) {
        return Optional.ofNullable(catalog().byId().get(id));
    }

    @Override
    public List<Food> search(String text, String language, int limit) {
        String wanted = FoodCatalog.normalize(text);
        if (wanted.isEmpty()) {
            return List.of();
        }
        List<Indexed> index = catalog().index();
        Comparator<Indexed> byName = Comparator.comparing(entry -> entry.name(language));
        Stream<Indexed> startingWith =
                index.stream().filter(entry -> entry.name(language).startsWith(wanted)).sorted(byName);
        Stream<Indexed> containing = index.stream()
                .filter(entry -> !entry.name(language).startsWith(wanted)
                        && entry.name(language).contains(wanted))
                .sorted(byName);
        return Stream.concat(startingWith, containing)
                .limit(limit)
                .map(Indexed::food)
                .toList();
    }

    @Override
    public Optional<Food> findByName(String name) {
        String wanted = FoodCatalog.normalize(name);
        return catalog().index().stream()
                .filter(entry -> entry.es().equals(wanted) || entry.en().equals(wanted))
                .map(Indexed::food)
                .findFirst();
    }

    private Loaded catalog() {
        Loaded current = loaded;
        if (current == null) {
            List<Food> foods = repository.findAll().stream().map(FoodEntity::toFood).toList();
            current = new Loaded(
                    foods.stream().collect(Collectors.toMap(Food::id, Function.identity())),
                    foods.stream()
                            .map(food -> new Indexed(
                                    food, FoodCatalog.normalize(food.nameEs()), FoodCatalog.normalize(food.nameEn())))
                            .toList());
            loaded = current;
        }
        return current;
    }
}
