# SDK verification

Customer-style contract tests for a Kotlin SDK release candidate consumed through Maven coordinates.

This suite verifies the individual Experiment and Session Replay blade integrations without depending on Engagement or the Unified Wrapper.

## Verification model

| Gate | Purpose |
|---|---|
| Artifact resolution | Consumes the Kotlin SDK candidate and released blades through Maven coordinates, then verifies the resolved versions |
| Amplitude Analytics host | Verifies each blade with the standard Kotlin SDK lifecycle and event pipeline |
| Third-party analytics host | Verifies blades against `AnalyticsClient`, `PluginHost`, and `UniversalPlugin` without the Amplitude Analytics implementation |
| Java compatibility | Verifies Java constructors and `UniversalPlugin` compatibility |

`NonAmplitudeAnalyticsHost` is a minimal test provider. It opts into guarded Kotlin SDK host APIs to exercise the integration boundary intended for Unified and third-party analytics providers.

## Experiment coverage

| Test file | Customer setup | Gate |
|---|---|---:|
| `ExperimentPluginIntegrationTest` | Registration timing; identity/session; reset; opt-out; exposure; duplicate registration; wrapped client | Yes |
| `ExperimentPluginConfigurationTest` | Default/explicit keys; fetch-on-start; headers; initial local flags | Yes |
| `ExperimentPluginMultipleInstancesTest` | Distinct deployment keys remain isolated | Yes |
| `ExperimentPluginLifecycleTest` | Starts opted out; Analytics reset semantics | Yes |
| `ExperimentLegacyIntegrationTest` | Existing Analytics integration remains compatible | Yes |
| `ExperimentPluginJavaCompatibilityTest` | Java constructors and `UniversalPlugin` compatibility | Yes |
| `ExperimentPluginNonAmplitudeHostTest` | Third-party identity/session provider and exposure event sink | Yes |

## Session Replay coverage

| Test file | Customer setup | Gate |
|---|---|---:|
| `SessionReplayPluginIntegrationTest` | Registration/accessor; identity and reset; opt-out; custom session; event enrichment; removal | Yes |
| `SessionReplayPluginIntegrationTest` | Duplicate registration keeps the first plugin | Yes |
| `SessionReplayPluginIntegrationTest` | Existing Session Replay client remains caller owned | Yes |
| `SessionReplayPluginJavaCompatibilityTest` | Java constructors and `UniversalPlugin` compatibility | Yes |
| `SessionReplayPluginNonAmplitudeHostTest` | Third-party identity/session/opt-out provider and event pipeline | Yes |
