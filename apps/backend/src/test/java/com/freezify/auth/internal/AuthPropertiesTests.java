package com.freezify.auth.internal;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class AuthPropertiesTests {

    @Test
    void rejectsSecretsTooShortForHs256() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new AuthProperties("too-short", "freezify", Duration.ofMinutes(15), Duration.ofDays(30)))
                .withMessageContaining("FREEZIFY_JWT_SECRET");
    }

    @Test
    void acceptsA256BitSecret() {
        assertThatCode(() -> new AuthProperties("x".repeat(32), "freezify", Duration.ofMinutes(15), Duration.ofDays(30)))
                .doesNotThrowAnyException();
    }
}
