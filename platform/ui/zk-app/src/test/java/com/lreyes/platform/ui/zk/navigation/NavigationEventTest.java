package com.lreyes.platform.ui.zk.navigation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the NavigationEvent value object.
 */
class NavigationEventTest {

    @Test
    @DisplayName("Constructor stores page and forceReload flag")
    void constructor_storesFields() {
        NavigationEvent event = new NavigationEvent("~./zul/customers.zul", true);

        assertEquals("~./zul/customers.zul", event.getPage());
        assertTrue(event.isForceReload());
    }

    @Test
    @DisplayName("forceReload=false is stored correctly")
    void constructor_forceReloadFalse() {
        NavigationEvent event = new NavigationEvent("~./zul/dashboard.zul", false);

        assertFalse(event.isForceReload());
        assertEquals("~./zul/dashboard.zul", event.getPage());
    }

    @Test
    @DisplayName("page may be null — constructor does not throw")
    void constructor_nullPage_doesNotThrow() {
        assertDoesNotThrow(() -> new NavigationEvent(null, false));
    }

    @Test
    @DisplayName("Two events with same data are logically equivalent")
    void events_withSameData_areEquivalent() {
        NavigationEvent a = new NavigationEvent("~./zul/test.zul", true);
        NavigationEvent b = new NavigationEvent("~./zul/test.zul", true);

        assertEquals(a.getPage(), b.getPage());
        assertEquals(a.isForceReload(), b.isForceReload());
    }
}
