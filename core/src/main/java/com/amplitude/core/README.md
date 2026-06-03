# Plugins & Identity — `com.amplitude.core`

How the SDK is extended (plugins) and how identity (userId / deviceId / sessionId)
reaches those plugins. Read this before touching `Plugin`, `Timeline`, `State`,
`IdentityCoordinator`, or `com.amplitude.id`.

---

## 1. Plugins

A **plugin** observes or transforms the SDK. Each declares a `Plugin.Type` that
decides *where* it runs:

| Type | In the event path? | Lives in |
|------|--------------------|----------|
| `Before` / `Enrichment` / `Destination` | yes | a `Timeline` mediator |
| `Utility` | no | a `Timeline` mediator |
| `Observe` | no | see below |

Two ways to reach the "observe" world — **both receive the state callbacks below**:

- An **`ObservePlugin`** (abstract class) is routed by `Amplitude.add()` to the
  `State` store.
- A plain **`Plugin` with `type = Observe`** lands in the timeline's `Observe`
  mediator.

### Lifecycle & lookup
- `add(plugin)` — if `plugin.name != null`, any existing plugin with that name is
  removed (and `teardown()`-ed) first. Dedup is **best-effort**: `add()` is expected
  from a single thread.
- `findPlugin<T>()` — first match assignable to `T`, across the timeline mediators
  **and** the store (store iteration is snapshotted under its lock).

---

## 2. State-change callbacks

`Plugin` exposes default no-op callbacks, so existing plugins are unaffected:

- `onUserIdChanged(userId)` / `onDeviceIdChanged(deviceId)`
- `onSessionIdChanged(sessionId)` *(Android only — it owns sessions)*
- `onOptOutChanged(optOut)`
- `onReset()` *(bundled with the userId/deviceId change from a reset)*

**One fan-out path.** Every callback goes through `Amplitude.notifyPlugins`, which
snapshots `timeline + store` **once** and invokes each plugin inside `safelyNotify`
(try/catch + log). A throwing plugin can't break the loop, the calling thread, or
the Android event-message coroutine. `reset()` reuses a single snapshot for its
three passes (userId → deviceId → reset), so a plugin added *during* a callback
doesn't receive that reset's callbacks but is immediately findable afterward.

**Semantics: intent-based.** Every explicit `setUserId` / `setDeviceId` / `optOut` /
`reset` notifies once, whether or not the value changed. Delivery is best-effort,
latest-value: under concurrent setters notifications may coalesce, but the value a
plugin sees is always consistent with the instance's current identity. Identity
mutation is expected from a single thread.

---

## 3. Identity — two layers, lock released before notify

Identity has exactly two owners:

- **`IdentityCoordinator`** (`internal`, this package) — the **runtime** owner:
  the in-memory mirror in `State`, pre-build *pending* intent, and `bootstrap()`
  reconciliation (user intent wins over persisted). Every mutation is serialized on
  one lock so the `State` write and the persistence commit are atomic.
- **`IdentityManager`** (`com.amplitude.id`, shared with the Experiment SDK) —
  durable **persistence** only. Treat it as a stable, public boundary; don't push
  Amplitude-specific concerns into it.

`State` is a silent `@Volatile` mirror for synchronous reads (event enrichment,
callback values). It does **not** notify — notification belongs to `Amplitude`.

### Reentrancy (the subtle part)
`Amplitude.setUserId` calls the coordinator, then fans callbacks out **after the
lock is released**. JVM `synchronized` is reentrant, so a "notify while holding the
lock" design would let a plugin's `onUserIdChanged` call `setUserId` reentrantly,
fully commit the inner value, then have the *outer* commit clobber it. Committing
under the lock and notifying after releasing it makes the inner write win.

```
setUserId("a"): lock { store.userId = "a"; manager.commit() }   // lock released
                notifyPlugins { onUserIdChanged("a") }           // plugin may reentrantly setUserId("b")
                                                                  //   → fresh lock, commits "b", notifies "b"
                // outer does NOT write again → final = "b" ✓
```

### Invariants to preserve
- **No lock is held across any plugin callback** (`onX`, `setup`, `teardown`).
  Lock order is one-directional: coordinator lock → manager lock; the manager never
  calls back into core.
- `State.add`/`remove`/dedup call `setup()`/`teardown()` **outside** the `plugins`
  monitor.
- Timeline storage is `CopyOnWriteArrayList`; store iteration uses a `toList()`
  snapshot. Both are CME-immune.
