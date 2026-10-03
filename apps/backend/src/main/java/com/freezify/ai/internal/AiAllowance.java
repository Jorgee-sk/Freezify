package com.freezify.ai.internal;

import com.freezify.common.Today;
import java.time.LocalDate;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** How many calls to the model each person may cause per day. */
interface AiAllowance {

    /** Counts a call; {@code false} when it would go over today's allowance, and then the call must not be made. */
    boolean take(UUID userId);
}

/**
 * Counted in the database, so that restarting the backend does not hand out a new allowance and every instance
 * sees the same count. Rows older than a week are of no use and are removed as new ones arrive.
 */
@Component
class DatabaseAllowance implements AiAllowance {

    private static final Logger log = LoggerFactory.getLogger(DatabaseAllowance.class);
    private static final int KEPT_DAYS = 7;

    private final JdbcTemplate jdbc;
    private final Today today;
    private final int dailyCallsPerUser;

    DatabaseAllowance(JdbcTemplate jdbc, Today today, AiProperties properties) {
        this.jdbc = jdbc;
        this.today = today;
        this.dailyCallsPerUser = properties.dailyCallsPerUser();
    }

    @Override
    public boolean take(UUID userId) {
        LocalDate day = today.date();
        // One statement: two calls at once cannot both read the same count.
        Integer calls = jdbc.queryForObject(
                """
                insert into ai_usage (user_id, day, calls) values (?, ?, 1)
                on conflict (user_id, day) do update set calls = ai_usage.calls + 1
                returning calls
                """,
                Integer.class,
                userId,
                day);
        jdbc.update("delete from ai_usage where day < ?", day.minusDays(KEPT_DAYS));
        if (calls == null || calls > dailyCallsPerUser) {
            log.info("Daily language model allowance spent for a user");
            return false;
        }
        return true;
    }
}
