# Amplitude Kotlin SDK

Analytics SDK for Android. Two modules: **`core`** (pure Kotlin/JVM) and **`android`** (extends core with Android lifecycle, sessions, autocapture). Published on Maven Central as `com.amplitude:analytics-core` and `com.amplitude:analytics-android`.

## Build

```bash
./gradlew build -x test           # Build
./gradlew :android:test            # Android tests
./gradlew :core:test               # Core tests
./gradlew ktlintFormat             # Auto-format
./gradlew apiDump                  # Update API dumps after public API changes
./gradlew apiCheck                 # Binary compat check (CI enforced)
```

## What Gets Merged

### Code Quality

- Readability first. Simplest form is the best form.
- Explicit imports only — no wildcards.
- `ktlintFormat` before every commit.
- Open classes for public types. Data classes only for internal DTOs. Sealed classes for fixed types.
- Companion objects for constants and factory methods. No `@JvmStatic` or `@JvmField`.

### Public API Stability

This is a published SDK and we guard the public surface:

- **`@JvmOverloads`** on every public function and constructor with default parameters. Java callers depend on the generated overloads.
- **Never remove or rename public API.** Deprecate first: `@Deprecated("Please use X instead", ReplaceWith("x"))`.
- **Don't add new Configuration constructor parameters.** Evolve via `ConfigurationBuilder` instead. Both core and android have this pattern.
- Use **`internal`** for implementation classes, helpers, serialization constants, and extension functions. Only Plugin interfaces, Configuration, Amplitude, and event types are public.
- **`@RestrictedAmplitudeFeature`**, for restricting internal implementation details. **`@RequiresOptIn`** for features not yet ready for public use.
- Optional fields: `Type? = null`. Required fields: non-null with default value.
- Never expose `suspend` functions in public API — use `Deferred` or launch internally.

### Binary Compatibility

Public API changes require `./gradlew apiDump` and committed `.api` files (`core/api/core.api`, `android/api/android.api`). CI fails on stale dumps.

### Thread Safety

The SDK uses three dispatchers: `amplitudeDispatcher` (general), `networkIODispatcher` (HTTP), `storageIODispatcher` (file I/O).

- Deep-copy mutable data crossing thread boundaries. Shallow copies don't prevent CME on nested maps/lists.
- Identity updates must be synchronous in-memory, async to disk — callers expect `setUserId()` then `track()` ordering.
- Never dispatch pipeline data to `Dispatchers.Main` while other threads process the same event.
- Use `limitedParallelism(1)` for sequential-access resources.

### Memory and I/O

- Stream large data — never load full files into strings.
- Fail gracefully — compression, network, and storage failures fall back, not crash.
- Deep-copy events at boundaries with external consumers (plugins, bridges).

### Testing

- Every change ships with unit tests (JUnit 5, Robolectric, MockK, `kotlinx-coroutines-test`, MockWebServer).
  - Core: `core/src/test/kotlin/` | Android: `android/src/test/java/`
- Test methods use backtick descriptive names: `` `should not flush when offline` ``.
- `@Nested inner class` for grouping related tests within a test class.
- Coroutine tests: `runTest()` with `StandardTestDispatcher`, `advanceUntilIdle()`.

### Module Boundaries

- Android-specific code belongs in `:android`, not `:core`. Core stays platform-independent.
- New features typically mean a new plugin or extending an existing one. Events flow through the `Timeline` pipeline: Before, Enrichment, Destination, Utility, Observe.
- Key plugin interfaces: `Plugin`, `EventPlugin`, `DestinationPlugin`, `ObservePlugin` in `core/.../platform/Plugin.kt`.

### PRs

- Titles follow [conventional commits](https://www.conventionalcommits.org/): `feat:`, `fix:`, `perf:`, `refactor:`, `test:`, `docs:`, `chore:`. Drives `semantic-release` version bumps.
- Always use the repo's PR template (`.github/pull_request_template.md`). Don't replace it with free-form text.
- Keep descriptions succinct. Lead with the problem, then the solution. Reference Jira tickets when applicable.

## Build Configuration

- Version catalog: `gradle/libs.versions.toml`
- Version tracked in `gradle.properties` (`VERSION_NAME`)
