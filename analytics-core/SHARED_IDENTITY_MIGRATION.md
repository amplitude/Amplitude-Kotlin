# Shared identity migration

This release adds a runtime user-property snapshot to Analytics identity and a
`setIdentity` API for replacing user id, device id, and local properties together.
Existing method signatures remain available. Plugin callback behavior changes as
outlined below.

## Keep locally evaluated properties in Identify

Use `identify` for properties that Experiment, Guides and Surveys, or targeted
replay need on this device:

```kotlin
amplitude.identify(Identify().set("plan", "pro"))
```

Analytics applies `$clearAll`, `$unset`, `$set`, then plain keys to its local map.
Other Identify operations still upload but do not change the local map. Properties
are not persisted or fetched from the server, so this snapshot represents what
the app has supplied during the current process. Reset clears it.

User-property operations attached to ordinary events still upload. They do not
update this local snapshot. Move properties used for local targeting to Identify.
After migrating to host identity, integrations no longer recover these properties
from the legacy connector.

## Plugin callbacks

- Effective changes to local properties now call `onIdentityChanged` on every
  plugin, in addition to changes to user id and device id.
- Repeating `setUserId` or `setDeviceId` with the current value no longer calls
  the individual-id callback or `onIdentityChanged`. Use an explicit product
  fetch API if the app previously used repeated setters to trigger a fetch.
- `identify` with `EventOptions` user or device ids applies those ids and the
  Identify properties as one change. Plugins receive one `onIdentityChanged` with
  both, after the Identify is queued, instead of one per id followed by one for
  the properties. `onUserIdChanged` and `onDeviceIdChanged` still fire before the
  Identify is queued.
- Reset skips `onUserIdChanged(null)` when no user id was set. It still rotates
  device id, calls `onDeviceIdChanged`, delivers one complete identity, then calls
  `onReset`.
- Restoring persisted identity during initialization does not notify plugins,
  as in earlier releases. A plugin added before initialization completes can see
  null ids in `setup`; on Android, read identity again in `onSessionIdChanged`.

Plugin callbacks should remain fast and safe to call outside the main thread.

## Experiment versions

The legacy connector plugins remain installed. With Experiment 1.17.0, enabling
automatic identity fetching on its owned plugin causes an extra fetch after an
effective Identify property set:

| Analytics | Experiment | Owned plugin with automatic identity fetching enabled |
| --- | --- | --- |
| 1.35.0 | 1.17.0 | One automatic fetch for an effective Identify property set |
| Analytics with shared identity | 1.17.0 | Two automatic fetches through host and connector notifications |

Automatic identity fetching is off by default. With it enabled, clearing properties
can also trigger a request with stale connector properties on Experiment 1.17.0.

Hosts that implement `AnalyticsClient` must maintain `identity.userProperties`.
Plugins treat an empty map as a user without properties, as on iOS.

Experiment 1.17.0's owned integration may already prefer a nonempty host map over
connector properties even with automatic fetching disabled. Properties supplied
only through ordinary events can therefore disappear from local evaluation after
an Identify supplies a nonempty host map. Use Identify for those properties.

The extra host-callback fetch does not apply to Experiment's connector-only
`initializeWithAmplitudeAnalytics` integration or versions before the owned
UniversalPlugin integration. Their existing connector limitations remain.

## Whole-identity replacement

```kotlin
amplitude.setIdentity("user-123", amplitude.getDeviceId(), mapOf("plan" to "pro"))
```

The setter replaces the complete local identity, following iOS replacement
semantics. Omitting properties clears the local map. Changed nonempty properties
also generate an Identify `$set`. Removing a local key does not delete its server
value. Use `identify(Identify().unset("plan"))` or `clearAll()` for server removal.
Web's setter accepts partial updates instead.
