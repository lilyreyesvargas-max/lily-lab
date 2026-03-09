package com.lreyes.platform.ui.zk.vm;

import com.lreyes.platform.ui.zk.navigation.NavigationEvent;
import com.lreyes.platform.ui.zk.navigation.NavigationQueue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.zkoss.bind.annotation.Command;
import org.zkoss.bind.annotation.Init;
import org.zkoss.bind.annotation.NotifyChange;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for AssistantVM navigation logic.
 *
 * Verifies that AssistantVM publishes NavigationEvents to NavigationQueue
 * instead of (or in addition to) BindUtils.postGlobalCommand.
 */
class AssistantVMNavigationTest {

    private AssistantVM vm;

    @BeforeEach
    void setUp() {
        vm = new AssistantVM();
    }

    // ──────────────────────────────────────────────────────────────
    // postNavigateForceReload — publishes to NavigationQueue
    // ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("postNavigateForceReload publishes a NavigationEvent with forceReload=true")
    void postNavigateForceReload_publishesForceReloadEvent() {
        AtomicReference<NavigationEvent> captured = new AtomicReference<>();

        NavigationQueue fakeQueue = new NavigationQueue() {
            @Override
            public void subscribe(Consumer<NavigationEvent> handler) { /* no-op */ }
            @Override
            public void publish(NavigationEvent event) { captured.set(event); }
            @Override
            public void unsubscribe() { /* no-op */ }
        };

        vm.setNavigationQueue(fakeQueue);
        vm.postNavigateForceReload("~./zul/customers.zul");

        assertNotNull(captured.get(), "A NavigationEvent must be published");
        assertEquals("~./zul/customers.zul", captured.get().getPage());
        assertTrue(captured.get().isForceReload(), "Event must have forceReload=true");
    }

    @Test
    @DisplayName("postNavigateForceReload with null page does not publish")
    void postNavigateForceReload_withNullPage_doesNotPublish() {
        AtomicReference<NavigationEvent> captured = new AtomicReference<>();

        NavigationQueue fakeQueue = new NavigationQueue() {
            @Override
            public void subscribe(Consumer<NavigationEvent> handler) { /* no-op */ }
            @Override
            public void publish(NavigationEvent event) { captured.set(event); }
            @Override
            public void unsubscribe() { /* no-op */ }
        };

        vm.setNavigationQueue(fakeQueue);
        vm.postNavigateForceReload(null);

        assertNull(captured.get(), "No event must be published for null page");
    }

    @Test
    @DisplayName("postNavigateForceReload with blank page does not publish")
    void postNavigateForceReload_withBlankPage_doesNotPublish() {
        AtomicReference<NavigationEvent> captured = new AtomicReference<>();

        NavigationQueue fakeQueue = new NavigationQueue() {
            @Override
            public void subscribe(Consumer<NavigationEvent> handler) { /* no-op */ }
            @Override
            public void publish(NavigationEvent event) { captured.set(event); }
            @Override
            public void unsubscribe() { /* no-op */ }
        };

        vm.setNavigationQueue(fakeQueue);
        vm.postNavigateForceReload("  ");

        assertNull(captured.get(), "No event must be published for blank page");
    }

    // ──────────────────────────────────────────────────────────────
    // postNavigate — publishes NavigationEvent with forceReload=false
    // ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("postNavigate publishes a NavigationEvent with forceReload=false")
    void postNavigate_publishesRegularNavigationEvent() {
        AtomicReference<NavigationEvent> captured = new AtomicReference<>();

        NavigationQueue fakeQueue = new NavigationQueue() {
            @Override
            public void subscribe(Consumer<NavigationEvent> handler) { /* no-op */ }
            @Override
            public void publish(NavigationEvent event) { captured.set(event); }
            @Override
            public void unsubscribe() { /* no-op */ }
        };

        vm.setNavigationQueue(fakeQueue);
        vm.postNavigate("~./zul/employees.zul");

        assertNotNull(captured.get(), "A NavigationEvent must be published");
        assertEquals("~./zul/employees.zul", captured.get().getPage());
        assertFalse(captured.get().isForceReload(), "Regular navigate must have forceReload=false");
    }

    // ──────────────────────────────────────────────────────────────
    // sendMessage — annotation correctness
    // ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("sendMessage has @Command annotation")
    void sendMessage_hasCommandAnnotation() throws Exception {
        Method m = AssistantVM.class.getDeclaredMethod("sendMessage");
        assertNotNull(m.getAnnotation(Command.class), "sendMessage must carry @Command");
    }

    @Test
    @DisplayName("sendMessage notifies messages and userInput")
    void sendMessage_notifiesMessagesAndUserInput() throws Exception {
        Method m = AssistantVM.class.getDeclaredMethod("sendMessage");
        NotifyChange nc = m.getAnnotation(NotifyChange.class);
        assertNotNull(nc, "sendMessage must carry @NotifyChange");
        List<String> props = Arrays.asList(nc.value());
        assertTrue(props.contains("messages"), "@NotifyChange must include 'messages'");
        assertTrue(props.contains("userInput"), "@NotifyChange must include 'userInput'");
    }

    // ──────────────────────────────────────────────────────────────
    // getMessages / getUserInput — bound properties exist
    // ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("getMessages returns a non-null list")
    void getMessages_returnsNonNullList() {
        assertNotNull(vm.getMessages());
    }

    @Test
    @DisplayName("getUserInput getter exists and returns null initially")
    void getUserInput_returnsNullInitially() {
        assertNull(vm.getUserInput());
    }

    @Test
    @DisplayName("setUserInput stores value retrievable via getUserInput")
    void setUserInput_roundtrip() {
        vm.setUserInput("hola");
        assertEquals("hola", vm.getUserInput());
    }

    // ──────────────────────────────────────────────────────────────
    // sendMessage — empty / null input guard
    // ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("sendMessage with null userInput adds no user message")
    void sendMessage_withNullInput_addsNoMessage() {
        int sizeBefore = vm.getMessages().size();
        vm.setUserInput(null);
        vm.sendMessage();
        // Messages list should not grow beyond the initial greeting that was
        // added by init(); since init() needs ZK session we check only that
        // it does not throw and messages size does not change
        assertEquals(sizeBefore, vm.getMessages().size());
    }

    @Test
    @DisplayName("sendMessage with blank userInput adds no user message")
    void sendMessage_withBlankInput_addsNoMessage() {
        int sizeBefore = vm.getMessages().size();
        vm.setUserInput("   ");
        vm.sendMessage();
        assertEquals(sizeBefore, vm.getMessages().size());
    }

    // ──────────────────────────────────────────────────────────────
    // clearChat — @Command annotation
    // ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("clearChat has @Command annotation")
    void clearChat_hasCommandAnnotation() throws Exception {
        Method m = AssistantVM.class.getDeclaredMethod("clearChat");
        assertNotNull(m.getAnnotation(Command.class), "clearChat must carry @Command");
    }

    @Test
    @DisplayName("clearChat resets messages list")
    void clearChat_resetsMessages() {
        // Manually add messages
        vm.getMessages().add(new com.lreyes.platform.ui.zk.model.AssistantMessage("test", true));
        vm.clearChat();
        // After clear, the list should contain only the reset greeting
        assertFalse(vm.getMessages().isEmpty(), "clearChat must add a reset greeting");
        assertFalse(vm.getMessages().get(0).isFromUser(),
                "Post-clear message must be from bot (not user)");
    }
}
