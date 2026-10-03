package com.freezify.ai.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.freezify.testsupport.ApiTestSupport;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** The daily allowance is kept in the database: a restart does not hand out a new one. */
class DatabaseAllowanceTests extends ApiTestSupport {

    @Autowired
    private AiAllowance allowance;

    @Autowired
    private AiProperties properties;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void eachPersonHasADailyAllowance() throws Exception {
        UUID ana = UUID.fromString(register("Ana").id());
        UUID luis = UUID.fromString(register("Luis").id());

        for (int call = 1; call <= properties.dailyCallsPerUser(); call++) {
            assertThat(allowance.take(ana)).as("call %d", call).isTrue();
        }
        assertThat(allowance.take(ana)).isFalse();
        // Someone else still has theirs.
        assertThat(allowance.take(luis)).isTrue();

        // A new day, a new allowance.
        clock.advance(Duration.ofDays(1));
        assertThat(allowance.take(ana)).isTrue();
    }

    @Test
    void oldDaysAreForgotten() throws Exception {
        UUID ana = UUID.fromString(register("Ana").id());
        LocalDate today = LocalDate.now(ZoneId.of("Europe/Madrid"));
        jdbc.update("insert into ai_usage (user_id, day, calls) values (?, ?, 3)", ana, today.minusDays(8));
        jdbc.update("insert into ai_usage (user_id, day, calls) values (?, ?, 3)", ana, today.minusDays(6));

        allowance.take(ana);

        assertThat(jdbc.queryForList("select day from ai_usage where user_id = ? order by day", LocalDate.class, ana))
                .containsExactly(today.minusDays(6), today);
    }
}
