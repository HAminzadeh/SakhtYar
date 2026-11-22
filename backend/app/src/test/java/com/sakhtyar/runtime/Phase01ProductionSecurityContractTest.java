package com.sakhtyar.runtime;

import static org.junit.jupiter.api.Assertions.*;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class Phase01ProductionSecurityContractTest {
    private String productionConfig() throws Exception {
        try (InputStream stream = getClass().getClassLoader()
                .getResourceAsStream("application-production.yml")) {
            assertNotNull(stream, "production configuration resource must be packaged");
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void productionRequiresExplicitSecretsAndCredentialedCorsOrigin() throws Exception {
        String config = productionConfig();
        for (String required : new String[] {
                "${SAKHTYAR_RUNTIME_DB_USER}", "${SAKHTYAR_RUNTIME_DB_PASSWORD}",
                "${APP_JWT_SECRET}", "${APP_BOOTSTRAP_ADMIN_PASSWORD}",
                "${MINIO_ACCESS_KEY}", "${MINIO_SECRET_KEY}", "${CORS_ALLOWED_ORIGINS}"
        }) assertTrue(config.contains(required), "Production must fail closed without " + required);
        assertTrue(config.contains("secure: true"), "Production cookies must require TLS");
        assertTrue(config.contains("same-site: Strict"));
        assertTrue(config.contains("enabled: false"), "Production TEST seed must stay disabled");
        assertFalse(config.contains("ChangeMeNow_123!"));
        assertFalse(config.contains("change-this-development-jwt-secret"));
    }
}
