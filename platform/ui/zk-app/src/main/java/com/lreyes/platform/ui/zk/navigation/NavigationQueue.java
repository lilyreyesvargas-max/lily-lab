package com.lreyes.platform.ui.zk.navigation;

import java.util.function.Consumer;

/**
 * Contract for the navigation event channel.
 *
 * <p>Implementations must be scoped to a single ZK {@link org.zkoss.zk.ui.Desktop}
 * so that events are delivered only within the same browser tab.</p>
 *
 * <p>The interface is deliberately narrow so that unit tests can substitute
 * a no-ZK-runtime fake easily.</p>
 */
public interface NavigationQueue {

    /**
     * Registers {@code handler} to receive {@link NavigationEvent}s published on this queue.
     * Calling subscribe again replaces the previous handler.
     */
    void subscribe(Consumer<NavigationEvent> handler);

    /**
     * Publishes a {@link NavigationEvent} to all current subscribers of this queue.
     *
     * @param event the event to dispatch; must not be {@code null}
     */
    void publish(NavigationEvent event);

    /**
     * Removes the current subscription. Safe to call even if not currently subscribed.
     * Must be called when the owning ViewModel is being destroyed to avoid memory leaks.
     */
    void unsubscribe();
}
