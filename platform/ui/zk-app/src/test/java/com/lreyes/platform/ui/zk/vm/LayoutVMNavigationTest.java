package com.lreyes.platform.ui.zk.vm;

import com.lreyes.platform.ui.zk.navigation.NavigationEvent;
import com.lreyes.platform.ui.zk.navigation.NavigationQueue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.zkoss.bind.annotation.GlobalCommand;
import org.zkoss.bind.annotation.NotifyChange;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for LayoutVM navigation logic.
 *
 * Strategy: LayoutVM exposes a package-level setter for NavigationQueue
 * so tests can inject a fake queue that records subscription / published events.
 * No ZK runtime is started.
 */
class LayoutVMNavigationTest {

    private LayoutVM vm;

    @BeforeEach
    void setUp() {
        vm = new LayoutVM();
    }

    // ──────────────────────────────────────────────────────────────
    // NavigationQueue subscription
    // ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("subscribeToNavigationQueue registers a consumer on the queue")
    void subscribeToNavigationQueue_registersConsumer() {
        AtomicReference<Consumer<NavigationEvent>> captured = new AtomicReference<>();

        NavigationQueue fakeQueue = new NavigationQueue() {
            @Override
            public void subscribe(Consumer<NavigationEvent> handler) {
                captured.set(handler);
            }
            @Override
            public void publish(NavigationEvent event) { /* no-op */ }
            @Override
            public void unsubscribe() { /* no-op */ }
        };

        vm.setNavigationQueue(fakeQueue);
        vm.subscribeToNavigationQueue();

        assertNotNull(captured.get(), "subscribe() must be called with a non-null consumer");
    }

    // ──────────────────────────────────────────────────────────────
    // handleNavigationEvent — page changes state
    // ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("handleNavigationEvent sets currentPage when event carries a non-null page")
    void handleNavigationEvent_setsCurrentPage() {
        vm.handleNavigationEvent(new NavigationEvent("~./zul/customers.zul", false));

        assertEquals("~./zul/customers.zul", vm.getCurrentPage());
    }

    @Test
    @DisplayName("handleNavigationEvent ignores null page — currentPage unchanged")
    void handleNavigationEvent_ignoresNullPage() {
        // Pre-condition: set currentPage via navigateForceReload to a known value
        vm.navigateForceReload("~./zul/dashboard.zul");

        vm.handleNavigationEvent(new NavigationEvent(null, false));

        assertEquals("~./zul/dashboard.zul", vm.getCurrentPage());
    }

    @Test
    @DisplayName("handleNavigationEvent ignores blank page — currentPage unchanged")
    void handleNavigationEvent_ignoresBlankPage() {
        vm.navigateForceReload("~./zul/dashboard.zul");

        vm.handleNavigationEvent(new NavigationEvent("  ", false));

        assertEquals("~./zul/dashboard.zul", vm.getCurrentPage());
    }

    // ──────────────────────────────────────────────────────────────
    // navigateForceReload @GlobalCommand — annotation correctness
    // ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("navigateForceReload has @GlobalCommand annotation")
    void navigateForceReload_hasGlobalCommandAnnotation() throws Exception {
        Method m = findNavigateForceReload();
        assertNotNull(m.getAnnotation(GlobalCommand.class),
                "navigateForceReload must carry @GlobalCommand");
    }

    @Test
    @DisplayName("navigateForceReload notifies currentPage")
    void navigateForceReload_notifiesCurrentPage() throws Exception {
        Method m = findNavigateForceReload();
        NotifyChange nc = m.getAnnotation(NotifyChange.class);
        assertNotNull(nc, "navigateForceReload must carry @NotifyChange");
        List<String> props = Arrays.asList(nc.value());
        assertTrue(props.contains("currentPage"),
                "@NotifyChange must include 'currentPage'");
    }

    // ──────────────────────────────────────────────────────────────
    // navigateForceReload — state logic (no ZK runtime needed)
    // ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("navigateForceReload sets currentPage to given page")
    void navigateForceReload_setsCurrentPage() {
        vm.navigateForceReload("~./zul/employees.zul");

        assertEquals("~./zul/employees.zul", vm.getCurrentPage());
    }

    @Test
    @DisplayName("navigateForceReload with null page does nothing")
    void navigateForceReload_withNullPage_doesNothing() {
        // currentPage stays null (not yet initialized via init())
        vm.navigateForceReload(null);

        assertNull(vm.getCurrentPage());
    }

    @Test
    @DisplayName("navigateForceReload with same page still updates currentPage reference")
    void navigateForceReload_withSamePage_updatesCurrentPage() {
        String page = "~./zul/customers.zul";
        vm.navigateForceReload(page);
        // Second call with same page — must still run (force reload semantics)
        vm.navigateForceReload(page);

        assertEquals(page, vm.getCurrentPage());
    }

    // ──────────────────────────────────────────────────────────────
    // navigateTo @GlobalCommand — annotation correctness
    // ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("navigateTo has @GlobalCommand annotation")
    void navigateTo_hasGlobalCommandAnnotation() throws Exception {
        Method m = LayoutVM.class.getDeclaredMethod("navigateTo", String.class);
        assertNotNull(m.getAnnotation(GlobalCommand.class),
                "navigateTo must carry @GlobalCommand");
    }

    @Test
    @DisplayName("navigateTo sets currentPage when page is non-null")
    void navigateTo_setsCurrentPage() {
        vm.navigateTo("~./zul/workflow.zul");

        assertEquals("~./zul/workflow.zul", vm.getCurrentPage());
    }

    @Test
    @DisplayName("navigateTo ignores null page")
    void navigateTo_withNullPage_doesNothing() {
        vm.navigateTo("~./zul/dashboard.zul");
        vm.navigateTo(null);

        assertEquals("~./zul/dashboard.zul", vm.getCurrentPage());
    }

    // ──────────────────────────────────────────────────────────────
    // NavigationQueue unsubscribe on destroy
    // ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("onDestroy unsubscribes from NavigationQueue")
    void onDestroy_unsubscribesFromQueue() {
        AtomicReference<Boolean> unsubscribed = new AtomicReference<>(false);

        NavigationQueue fakeQueue = new NavigationQueue() {
            @Override
            public void subscribe(Consumer<NavigationEvent> handler) { /* no-op */ }
            @Override
            public void publish(NavigationEvent event) { /* no-op */ }
            @Override
            public void unsubscribe() { unsubscribed.set(true); }
        };

        vm.setNavigationQueue(fakeQueue);
        vm.onDestroy();

        assertTrue(unsubscribed.get(), "onDestroy() must call queue.unsubscribe()");
    }

    // ──────────────────────────────────────────────────────────────
    // Helpers
    // ──────────────────────────────────────────────────────────────

    private Method findNavigateForceReload() throws NoSuchMethodException {
        return LayoutVM.class.getDeclaredMethod("navigateForceReload", String.class);
    }
}
