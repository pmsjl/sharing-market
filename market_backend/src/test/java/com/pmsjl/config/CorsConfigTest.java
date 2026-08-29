package com.pmsjl.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CorsConfigTest {

    @Test
    void acceptsCommaSeparatedProductionOrigins() {
        assertDoesNotThrow(() -> new CorsConfig(
                "https://market.example.com, https://*.pages.dev"
        ));
    }

    @Test
    void rejectsGlobalWildcardWhenCredentialsAreEnabled() {
        assertThrows(IllegalStateException.class, () -> new CorsConfig("*"));
    }

    @Test
    void rejectsEmptyOriginConfiguration() {
        assertThrows(IllegalStateException.class, () -> new CorsConfig(" , "));
    }
}
