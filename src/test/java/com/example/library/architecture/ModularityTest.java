package com.example.library.architecture;

import com.example.library.LibraryApplication;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

/**
 * Verifies the Spring Modulith application module structure: no cycles, no access
 * to other modules' internal packages, and dependencies declared only through
 * module APIs (base packages or {@code @NamedInterface} packages).
 */
class ModularityTest {

    @Test
    void verifyModularStructure() {
        ApplicationModules.of(LibraryApplication.class).verify();
    }
}
