package com.lreyes.platform.ui.zk.navigation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zkoss.zk.ui.event.Event;
import org.zkoss.zk.ui.event.EventListener;
import org.zkoss.zk.ui.event.EventQueues;

import java.util.function.Consumer;

/**
 * ZK {@link org.zkoss.zk.ui.event.EventQueue}-backed implementation of {@link NavigationQueue}.
 *
 * <p>Uses a named queue in {@link EventQueues#DESKTOP} scope so events are delivered
 * only within the same browser Desktop (tab). This solves the cross-binder communication
 * problem when the assistant panel lives in a different MVVM binder scope than
 * {@code LayoutVM}.</p>
 *
 * <h3>Why EventQueue instead of GlobalCommand?</h3>
 * <ul>
 *   <li>{@code BindUtils.postGlobalCommand} dispatches to all binders whose root component
 *       is present in the component tree at call time. When an {@code <include>} reloads,
 *       the inner binder is temporarily absent — the event gets lost.</li>
 *   <li>ZK {@code EventQueue} in DESKTOP scope delivers directly to registered listeners,
 *       bypassing binder scope. The listener (registered in {@code LayoutVM.@AfterCompose})
 *       persists for the lifetime of the Desktop.</li>
 * </ul>
 *
 * <h3>Event data format</h3>
 * The event name is {@value #QUEUE_NAME}.
 * The event data is the {@link NavigationEvent} instance.
 */
public final class DesktopNavigationQueue implements NavigationQueue {

    static final String QUEUE_NAME = "platformNavigation";

    private static final Logger log = LoggerFactory.getLogger(DesktopNavigationQueue.class);

    private EventListener<Event> activeListener;

    @Override
    public void subscribe(Consumer<NavigationEvent> handler) {
        unsubscribe(); // remove any previous listener first

        activeListener = event -> {
            Object data = event.getData();
            if (data instanceof NavigationEvent) {
                try {
                    handler.accept((NavigationEvent) data);
                } catch (Exception e) {
                    log.error("Error handling NavigationEvent in LayoutVM", e);
                }
            }
        };

        EventQueues.lookup(QUEUE_NAME, EventQueues.DESKTOP, true)
                .subscribe(activeListener);

        log.debug("LayoutVM subscribed to {} queue", QUEUE_NAME);
    }

    @Override
    public void publish(NavigationEvent event) {
        if (event == null) return;
        try {
            EventQueues.lookup(QUEUE_NAME, EventQueues.DESKTOP, true)
                    .publish(new Event(QUEUE_NAME, null, event));
            log.debug("Published NavigationEvent page={} forceReload={}",
                    event.getPage(), event.isForceReload());
        } catch (Exception e) {
            // Queue may not exist yet (e.g. LayoutVM not yet loaded) — log and ignore
            log.warn("Could not publish NavigationEvent (queue not ready?): {}", e.getMessage());
        }
    }

    @Override
    public void unsubscribe() {
        if (activeListener == null) return;
        try {
            var queue = EventQueues.lookup(QUEUE_NAME, EventQueues.DESKTOP, false);
            if (queue != null) {
                queue.unsubscribe(activeListener);
            }
        } catch (Exception e) {
            log.warn("Error unsubscribing from {} queue: {}", QUEUE_NAME, e.getMessage());
        } finally {
            activeListener = null;
        }
    }
}
