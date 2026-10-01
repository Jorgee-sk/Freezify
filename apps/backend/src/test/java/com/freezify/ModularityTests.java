package com.freezify;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ModularityTests {

    /** Fails on dependency cycles between modules and on access to another module's internal packages. */
    @Test
    void modulesRespectTheirBoundaries() {
        ApplicationModules.of(FreezifyApplication.class).verify();
    }
}
