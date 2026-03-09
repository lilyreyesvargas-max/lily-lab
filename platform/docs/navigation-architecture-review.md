# Architectural Review: ZK Navigation & AssistantVM-LayoutVM Communication

**Files Reviewed**:
- `ui/zk-app/src/main/java/com/lreyes/platform/ui/zk/vm/LayoutVM.java`
- `ui/zk-app/src/main/java/com/lreyes/platform/ui/zk/vm/AssistantVM.java`
- `ui/zk-app/src/main/resources/web/zul/index.zul`
- `ui/zk-app/src/main/resources/web/zul/assistant-panel.zul`

**Review Date**: 2026-03-09
**Reviewer**: Architecture Review Agent

**Overall Assessment**: NEEDS REWORK -- The communication mechanism between AssistantVM and LayoutVM has a fundamental architectural flaw rooted in ZK's binder scoping model.

---

## Executive Summary

**Verdict**: The `BindUtils.postGlobalCommand()` call from AssistantVM **cannot reach** LayoutVM's `@GlobalCommand` methods because the two ViewModels live in **different ZK binder scopes**. This is not a timing bug or a race condition -- it is a structural impossibility given the current ZUL layout. The assistant panel (`<div>` with `viewModel="@id('avm')..."`) is a sibling of the `<borderlayout>` (with `viewModel="@id('vm')..."`), and each creates its own independent binder instance. ZK's `postGlobalCommand(null, null, ...)` dispatches the command to **all binders on the same Desktop**, but the issue is more subtle than that: the `@NotifyChange("currentPage")` annotation on the `@GlobalCommand` methods in LayoutVM triggers a binding update on the `<include src="@load(vm.currentPage)">` component. If the global command does reach the binder (which it should for same-Desktop dispatch), the problem likely lies in the **force-reload mechanism** interacting badly with the Include component's lifecycle.

Let me be precise: there are **two distinct problems** to untangle.

---

## Problem 1: Does `postGlobalCommand` Cross Binder Boundaries?

### How ZK Global Commands Work

In ZK 10 MVVM, `BindUtils.postGlobalCommand(queueName, eventName, command, args)` works as follows:

1. When `queueName` is `null`, ZK uses the default queue scope.
2. The default queue scope in ZK is **Desktop-scoped** (since ZK 8+).
3. A `@GlobalCommand` annotated method will receive the command **if its binder is registered on the same Desktop**.

Since both the `<borderlayout>` (LayoutVM) and the `<div>` inside the assistant container (AssistantVM) are part of the same `index.zul` page, they share the same ZK Desktop. Therefore, **in theory, `postGlobalCommand(null, null, "navigateForceReload", args)` should reach LayoutVM's `@GlobalCommand navigateForceReload`**.

### So Why Does It Fail?

The answer is **not** that the command never arrives. The answer is that even if the command arrives and `currentPage` is updated, the **navigation does not visually happen**. There are three likely causes:

#### Cause A: The Include Component's Same-URL Optimization

The `<include>` component in ZK has an internal optimization: if you set `src` to the same value it already has, it does **nothing**. The `navigateForceReload` method in LayoutVM attempts to work around this:

```java
@GlobalCommand
@NotifyChange("currentPage")
public void navigateForceReload(@BindingParam("page") String page) {
    if (page == null) return;
    currentPage = page;
    if (mainInclude != null) {
        try {
            mainInclude.setSrc(null);
            mainInclude.setSrc(page);
        } catch (Exception ignored) {
            // @NotifyChange fallback handles the binding update
        }
    }
}
```

**Problem**: `mainInclude` is populated in `@AfterCompose` via `findInclude(view)`, which walks the component tree from the `view` parameter. The `view` parameter in `@AfterCompose` corresponds to the **component that declares the `viewModel`** -- in this case, the `<borderlayout>`. The `<include>` is a child of `<center>`, which is a child of `<borderlayout>`, so `findInclude` should find it. **However**, if the `@AfterCompose` does not fire (because it requires `@ContextParam(ContextType.VIEW)` and the binder's lifecycle is incomplete when the global command arrives from another binder), then `mainInclude` will be `null`, and the force-reload falls back to `@NotifyChange` only.

#### Cause B: @NotifyChange Does Not Trigger Include Reload for Same URL

When `@NotifyChange("currentPage")` fires and the binding `@load(vm.currentPage)` is re-evaluated, ZK's data binding infrastructure compares the new value with the old value. If `currentPage` was `"~./zul/employees.zul"` and it is set again to `"~./zul/employees.zul"`, the binding system sees **no change** and does **not** update the `<include>` component's `src` attribute.

This is the **core bug**: when the assistant creates an employee and wants to navigate to the employee list to show the new record, the user is likely **already on** `employees.zul` (or the page is the same target). The `@NotifyChange` fires, but the value is identical, so the Include does not reload.

#### Cause C: @AfterCompose May Not Be Invoked on LayoutVM

Looking more carefully at LayoutVM:

```java
@AfterCompose
public void afterCompose(@ContextParam(ContextType.VIEW) Component view) {
    findInclude(view);
}
```

This method is annotated `@AfterCompose` (not `@Init`). The `@AfterCompose` lifecycle phase in ZK MVVM is called after the binder has applied initial bindings and wired the component tree. This is correct for populating `mainInclude`. **But** there is a subtle issue: the `<include>` component's actual instance changes every time `src` is updated via binding, because ZK destroys and recreates the included content. So the `mainInclude` reference captured in `@AfterCompose` may point to a **stale component** that has already been detached from the live component tree.

This means `mainInclude.setSrc(null)` and `mainInclude.setSrc(page)` may be operating on a **detached/orphaned Include component** that is no longer in the DOM. The `try/catch` silently swallows any resulting exception, and the fallback `@NotifyChange` does not trigger a reload because the value is the same.

---

## Problem 2: Architectural Design Issues

### Issue 2.1: Two Independent Binders Is a Deliberate Choice, But Creates Coupling Problems

The decision to place the assistant panel **outside** the `<borderlayout>` is architecturally sound for visual layout purposes -- a floating overlay panel should not be constrained by the border layout's geometry. However, it creates two independent MVVM binder scopes that must communicate, and the chosen communication mechanism (global commands) is fragile.

**Assessment**: The visual layout decision is correct. The communication mechanism is wrong.

### Issue 2.2: Navigation Is an Implicit Side Effect of CRUD Operations

In `AssistantVM.processResponses()`:

```java
if (ok && navigateAfter != null) {
    postNavigateForceReload(navigateAfter);
}
```

Navigation is treated as a side effect of a successful CRUD operation. This mixes two concerns:
1. The assistant's responsibility: execute user commands and report results.
2. The shell's responsibility: manage which page is visible.

The assistant should not need to know **how** navigation works. It should only express **intent** ("I want the user to see the employee list now").

### Issue 2.3: Direct Include Manipulation Is Fragile

The `navigateForceReload` method directly manipulates the `Include` component via `mainInclude.setSrc(null); mainInclude.setSrc(page)`. This bypasses the MVVM binding layer and creates a hybrid approach where sometimes the binding drives the Include, and sometimes direct component manipulation does. These two mechanisms can conflict.

### Issue 2.4: TenantContext Leaks in AssistantVM

A secondary but important issue: virtually every `execute*` method in AssistantVM calls `TenantContext.setCurrentTenant(user.getTenantId())` but **never calls `TenantContext.clear()`**. Since ZK processes events in the servlet thread pool, this leaks tenant context to subsequent requests processed by the same thread.

```java
private boolean executeCreateCustomer() {
    // ...
    TenantContext.setCurrentTenant(user.getTenantId());  // SET
    getCustomerService().create(...);
    // ... NO CLEAR
    return true;
}
```

This is a **multi-tenancy data isolation bug** waiting to happen.

---

## Answers to the Five Questions

### Question 1: Is it correct that the assistant panel is OUTSIDE the Borderlayout?

**Yes, for visual layout. No, for binder communication.**

Placing the assistant outside the `<borderlayout>` is the right call for CSS positioning. A `position: fixed` floating panel should not be a child of a component that manages its children's layout through regions (north, west, center, etc.). Putting it inside would cause layout conflicts.

However, this means the assistant's binder and the layout's binder are independent. Any communication between them must go through a mechanism that works across binder boundaries. `postGlobalCommand` should theoretically work across binders on the same Desktop, but the **consequence** of the command (updating a binding to the same value, or manipulating a stale Include reference) is where the real failure occurs.

### Question 2: Does `BindUtils.postGlobalCommand(null, null, "navigateForceReload", args)` reach the Borderlayout's binder in ZK 10?

**Technically yes, practically it does not produce the desired effect.**

The global command dispatch mechanism in ZK 10 uses Desktop-scoped event queues by default. Both binders are on the same Desktop. The command **should** be delivered to LayoutVM's `navigateForceReload` method. The failure is not in command delivery; it is in what happens after:

1. If the page URL is the same as the current one, `@NotifyChange` sees no change and does nothing.
2. If `mainInclude` is stale (pointing to a detached component from a previous Include cycle), the direct manipulation does nothing useful.
3. The `catch (Exception ignored)` block silences all evidence of the failure.

### Question 3: Is there a fundamental architectural problem?

**Yes.** The fundamental problem is threefold:

1. **Same-value binding optimization**: ZK's binding engine will not re-apply a binding when the property value has not changed. You cannot navigate to the same page by setting `currentPage` to its existing value.
2. **Stale component reference**: The `mainInclude` reference captured in `@AfterCompose` goes stale after the first navigation, because the Include component's internals are rebuilt on each `src` change.
3. **Swallowed exceptions**: The `catch (Exception ignored)` pattern hides the failure, making debugging impossible.

### Question 4: What is the correct pattern for a floating panel to navigate the main shell in ZK MVVM?

There are three viable alternatives, ordered from simplest to most robust:

#### Alternative A: Client-Side Navigation via JavaScript Redirect (Simplest, Pragmatic)

Instead of trying to communicate between binders, have the assistant trigger a full page reload with a query parameter that tells LayoutVM which page to show:

```java
// In AssistantVM
private void navigateToPage(String page) {
    String encoded = java.net.URLEncoder.encode(page, java.nio.charset.StandardCharsets.UTF_8);
    Clients.evalJavaScript(
        "window.location.replace('/zul/index.zul?page=" + encoded + "')");
}
```

```java
// In LayoutVM.init()
String requestedPage = Executions.getCurrent().getParameter("page");
if (requestedPage != null) {
    currentPage = requestedPage;
} else if (resumePage != null) {
    currentPage = resumePage;
} else {
    currentPage = "~./zul/dashboard.zul";
}
```

**Pros**: Dead simple, always works, no binder coupling.
**Cons**: Full page reload (user sees flash), assistant panel state is lost (chat history resets unless persisted in session).

#### Alternative B: ZK EventQueue (Recommended -- Correct ZK Pattern)

Use ZK's `EventQueue` API with Desktop scope to decouple the two ViewModels. This is the **intended ZK mechanism** for cross-binder communication:

```java
// In AssistantVM -- publish navigation intent
private void postNavigateForceReload(String page) {
    EventQueue<Event> eq = EventQueues.lookup("navQueue", EventQueues.DESKTOP, true);
    eq.publish(new Event("onNavigate", null, page));
}
```

```java
// In LayoutVM -- subscribe to navigation events
@AfterCompose
public void afterCompose(@ContextParam(ContextType.VIEW) Component view) {
    findInclude(view);
    EventQueue<Event> eq = EventQueues.lookup("navQueue", EventQueues.DESKTOP, true);
    eq.subscribe(evt -> {
        String page = (String) evt.getData();
        if (page != null) {
            currentPage = page;
            // Force reload: use a sentinel value first
            if (mainInclude != null && mainInclude.getPage() != null) {
                mainInclude.setSrc(null);
                mainInclude.invalidate();
            }
            mainInclude.setSrc(page);
            BindUtils.postNotifyChange(null, null, this, "currentPage");
        }
    });
}
```

**Pros**: Proper ZK pattern for cross-binder communication. No full page reload. Decoupled -- AssistantVM does not need to know about LayoutVM.
**Cons**: Slightly more code. Must handle EventQueue cleanup on Desktop destroy.

#### Alternative C: Move Assistant Inside the Borderlayout's Binder Scope (Layout Restructure)

Restructure `index.zul` so that the floating assistant div is **inside** the `<borderlayout>` component tree, but positioned via CSS to appear floating:

```xml
<borderlayout viewModel="@id('vm') @init('...LayoutVM')" hflex="1" vflex="1">
    <north>...</north>
    <west>...</west>
    <center>
        <div hflex="1" vflex="1" style="position: relative;">
            <include src="@load(vm.currentPage)" hflex="1" vflex="1"/>
            <!-- Assistant floats over the content via CSS -->
            <div sclass="assistant-float-container" style="position: fixed; ...">
                <include src="~./zul/assistant-panel.zul"/>
            </div>
        </div>
    </center>
</borderlayout>
```

**Problem**: This puts the assistant inside the `<center>` region. The assistant-panel.zul creates its **own** binder (`viewModel="@id('avm')..."`), so it is still a separate binder scope. Being inside the Borderlayout's component tree does **not** merge the binders. The `@GlobalCommand` would still need to cross binder boundaries.

**This alternative does NOT solve the problem.** I include it to explain why the intuitive "just move it inside" approach would fail.

### Question 5: Should navigation be LayoutVM's responsibility or the Include mechanism's?

**Navigation must be LayoutVM's responsibility.** The `<include>` component is a dumb renderer -- it loads whatever `src` is set to. The LayoutVM is the shell orchestrator and should own the navigation state machine. The Include is just its rendering mechanism.

However, LayoutVM should expose navigation as a **service** (via EventQueue or a shared navigation service bean), not as a `@GlobalCommand` that depends on binder topology.

---

## Root Cause Summary

The navigation failure has **three compounding causes**:

| # | Cause | Severity |
|---|-------|----------|
| 1 | ZK binding does not re-apply when property value is unchanged (same-page navigation) | CRITICAL -- this alone explains most failures |
| 2 | `mainInclude` reference goes stale after first navigation because Include internals rebuild | HIGH -- explains failures even when page is different |
| 3 | `catch (Exception ignored)` swallows all evidence of failure | MEDIUM -- makes debugging impossible |

---

## Recommended Solution

### Priority 1 (CRITICAL): Fix the Navigation Mechanism

Use **Alternative B (EventQueue)** with the following implementation plan:

1. In `AssistantVM`: replace `BindUtils.postGlobalCommand` calls with `EventQueue.publish()`.
2. In `LayoutVM.afterCompose()`: subscribe to the EventQueue. In the subscriber, set `mainInclude.setSrc(null)`, then `mainInclude.setSrc(page)`, then call `BindUtils.postNotifyChange()` to sync the binding.
3. In `LayoutVM.afterCompose()`: instead of walking the tree once to find the Include, use `@Wire` or `Selectors.wireComponents()` to get a reliable reference. Alternatively, re-find the Include on every navigation event.
4. Remove the `catch (Exception ignored)` block and let failures propagate so they can be diagnosed.

### Priority 2 (HIGH): Fix the Stale Include Reference

Replace the tree-walking `findInclude` approach with one of:

- Option A: Give the `<include>` component an `id` in the ZUL (`id="mainInclude"`) and use `@Wire("#mainInclude")` with `Selectors.wireComponents(view, this, null)` in `@AfterCompose`.
- Option B: Re-find the Include every time navigation is triggered (walk the tree each time, not just once). This is slower but always correct.

### Priority 3 (HIGH): Fix TenantContext Leaks in AssistantVM

Every `execute*` method that calls `TenantContext.setCurrentTenant()` must be wrapped in try/finally:

```java
private boolean executeCreateCustomer() {
    Map<String, String> data = engine.getActiveFlow().getData();
    try {
        TenantContext.setCurrentTenant(user.getTenantId());
        getCustomerService().create(new CreateCustomerRequest(...));
        messages.add(msg("Cliente creado exitosamente."));
        return true;
    } catch (Exception e) {
        messages.add(msg("Error al crear cliente: " + e.getMessage()));
        return false;
    } finally {
        TenantContext.clear();
    }
}
```

This applies to **every** method in AssistantVM that calls `setCurrentTenant`: `executeCreateCustomer`, `executeCreateEmployee`, `executeStartProcess`, `executeCreateRole`, `executeUpdateRole`, `executeDeleteRole`, `executeCreateUser`, `executeUpdateUser`, `executeDeleteUser`, `executeCreateCatalog`, `executeUpdateCatalog`, `executeDeleteCatalog`, `executeAssignPermissions`, `loadAvailableRoles`, `searchEntity`, and all search methods.

### Priority 4 (MEDIUM): Remove Silent Exception Swallowing

Replace:
```java
} catch (Exception ignored) {
    // @NotifyChange fallback handles the binding update
}
```

With proper logging:
```java
} catch (Exception e) {
    org.slf4j.LoggerFactory.getLogger(LayoutVM.class)
        .warn("Failed to force-reload Include component for page: {}", page, e);
}
```

---

## Summary Table

| Issue | Severity | Effort | Fix |
|-------|----------|--------|-----|
| Global command does not trigger Include reload for same-page URL | CRITICAL | Medium | EventQueue + explicit Include manipulation |
| Stale `mainInclude` reference | HIGH | Low | `@Wire` annotation or re-find on each navigation |
| TenantContext leak in all AssistantVM execute methods | HIGH | Low | Add try/finally with `TenantContext.clear()` |
| Silent exception swallowing in `navigateForceReload` | MEDIUM | Trivial | Replace with logging |
| Navigation is mixed into CRUD side effects (coupling) | LOW | Medium | Refactor to NavigationService pattern (future) |

---

**Note to Architect**: The persistent failure across 3 fix attempts is expected -- the root cause is not in the communication channel (postGlobalCommand does deliver), but in the **effect** of the command: ZK's binding optimization prevents same-value updates, and the Include reference goes stale. The EventQueue approach sidesteps the binding layer entirely and manipulates the Include component directly in the subscriber callback, which is the correct solution.
