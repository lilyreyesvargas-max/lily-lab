package com.lreyes.platform.ui.zk.navigation;

/**
 * Immutable value object that carries a navigation request.
 *
 * <p>{@code page} — the ZUL path to navigate to (e.g. {@code "~./zul/customers.zul"}).<br>
 * {@code forceReload} — when {@code true} the include component is reset to {@code null}
 * first so that ZK re-creates the page even if the path has not changed.</p>
 */
public final class NavigationEvent {

    private final String page;
    private final boolean forceReload;

    public NavigationEvent(String page, boolean forceReload) {
        this.page = page;
        this.forceReload = forceReload;
    }

    /** The ZUL page path to navigate to. May be {@code null} (treated as no-op by handlers). */
    public String getPage() {
        return page;
    }

    /**
     * Whether the navigation must force a full reload of the include component.
     * Use {@code true} when navigating to the same page that is currently displayed
     * or when a fresh state is required after a CRUD operation in the assistant.
     */
    public boolean isForceReload() {
        return forceReload;
    }
}
