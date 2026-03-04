package com.lreyes.platform.core.tenancy;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class TenantPropertiesTest {

    @ParameterizedTest
    @ValueSource(strings = {"acme", "globex", "tenant_01", "ab", "a_very_long_tenant_name_123"})
    void validTenantNames_shouldPass(String name) {
        assertTrue(TenantProperties.isValidTenantName(name),
                "Debería ser válido: " + name);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",                    // vacío
            "a",                   // muy corto (mínimo 2 chars)
            "Acme",                // mayúscula
            "acme-corp",           // guión no permitido
            "123tenant",           // empieza con número
            "acme; DROP SCHEMA",   // SQL injection
            "acme\nDROP",          // newline injection
            "tenant.name",         // punto no permitido
    })
    void invalidTenantNames_shouldFail(String name) {
        assertFalse(TenantProperties.isValidTenantName(name),
                "Debería ser inválido: " + name);
    }

    @ParameterizedTest
    @ValueSource(strings = {"null_test"})
    void nullTenantName_shouldFail(String ignored) {
        assertFalse(TenantProperties.isValidTenantName(null));
    }
}
