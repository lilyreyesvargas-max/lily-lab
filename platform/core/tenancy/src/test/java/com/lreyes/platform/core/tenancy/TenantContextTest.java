package com.lreyes.platform.core.tenancy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TenantContextTest {

    @AfterEach
    void cleanup() {
        TenantContext.clear();
    }

    @Test
    void defaultTenant_shouldBePublic() {
        assertEquals("public", TenantContext.getCurrentTenant());
    }

    @Test
    void setAndGet_shouldReturnSetTenant() {
        TenantContext.setCurrentTenant("acme");
        assertEquals("acme", TenantContext.getCurrentTenant());
    }

    @Test
    void clear_shouldResetToDefault() {
        TenantContext.setCurrentTenant("acme");
        TenantContext.clear();
        assertEquals("public", TenantContext.getCurrentTenant());
    }

    @Test
    void optional_shouldBeEmptyWhenNotSet() {
        assertTrue(TenantContext.getCurrentTenantOptional().isEmpty());
    }

    @Test
    void optional_shouldBePresentWhenSet() {
        TenantContext.setCurrentTenant("globex");
        assertTrue(TenantContext.getCurrentTenantOptional().isPresent());
        assertEquals("globex", TenantContext.getCurrentTenantOptional().get());
    }

    @Test
    void threadIsolation_shouldNotLeakBetweenThreads() throws InterruptedException {
        TenantContext.setCurrentTenant("acme");

        Thread other = new Thread(() -> {
            // Otro hilo no debería ver "acme"
            assertEquals("public", TenantContext.getCurrentTenant());
        });
        other.start();
        other.join();

        // Hilo original sigue con "acme"
        assertEquals("acme", TenantContext.getCurrentTenant());
    }
}
