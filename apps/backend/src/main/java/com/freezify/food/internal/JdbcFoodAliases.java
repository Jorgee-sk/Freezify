package com.freezify.food.internal;

import com.freezify.food.FoodAliases;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Kept in {@code receipt_aliases}: rows without a household are shared, the rest belong to one. */
@Component
class JdbcFoodAliases implements FoodAliases {

    private static final int MAX_KEY = 120;

    private final JdbcTemplate jdbc;

    JdbcFoodAliases(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Map<String, UUID> shared() {
        return read("select text, food_id from receipt_aliases where household_id is null");
    }

    @Override
    public Map<String, UUID> learned(UUID householdId) {
        return read("select text, food_id from receipt_aliases where household_id = ?", householdId);
    }

    @Override
    public Optional<UUID> foodFor(UUID householdId, String name) {
        String key = FoodAliases.key(name);
        if (key.isEmpty()) {
            return Optional.empty();
        }
        // The household's own name wins over a shared one.
        return jdbc.queryForList(
                        """
                        select food_id from receipt_aliases
                        where text = ? and (household_id = ? or household_id is null)
                        order by household_id nulls last
                        limit 1
                        """,
                        UUID.class,
                        key,
                        householdId)
                .stream()
                .findFirst();
    }

    @Override
    public void remember(UUID householdId, String name, @Nullable UUID foodId) {
        String key = FoodAliases.key(name);
        if (key.isEmpty() || key.length() > MAX_KEY) {
            return;
        }
        if (foodId == null) {
            jdbc.update("delete from receipt_aliases where household_id = ? and text = ?", householdId, key);
            return;
        }
        jdbc.update(
                """
                insert into receipt_aliases (household_id, text, food_id, updated_at) values (?, ?, ?, now())
                on conflict (household_id, text) where household_id is not null
                do update set food_id = excluded.food_id, updated_at = excluded.updated_at
                """,
                householdId,
                key,
                foodId);
    }

    private Map<String, UUID> read(String sql, Object... arguments) {
        Map<String, UUID> aliases = new HashMap<>();
        jdbc.query(sql, row -> {
            aliases.put(row.getString("text"), row.getObject("food_id", UUID.class));
        }, arguments);
        return aliases;
    }
}
