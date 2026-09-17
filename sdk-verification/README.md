# SDK verification

Customer-style contract tests for a Kotlin SDK release candidate consumed through Maven coordinates.

This phase verifies the individual Experiment, Session Replay, and Engagement blades. Unified Wrapper verification is the next phase and will use the same customer-oriented structure when its API is available.

## Verification model

| Gate | Purpose |
|---|---|
| Artifact resolution | Consumes the Kotlin SDK candidate and released blades through Maven coordinates, then verifies the resolved versions |
| Amplitude Analytics host | Verifies each blade with the standard Kotlin SDK lifecycle and event pipeline |
| Third-party analytics host | Verifies blades against `AnalyticsClient`, `PluginHost`, and `UniversalPlugin` without the Amplitude Analytics implementation |
| Java compatibility | Verifies Java constructors, factories, and `UniversalPlugin` compatibility |
| Blade combinations | Verifies realistic multi-blade registration, enrichment, lookup, and removal |

`NonAmplitudeAnalyticsHost` is a minimal test provider. It opts into guarded Kotlin SDK host APIs to exercise the integration boundary intended for Unified and third-party analytics providers.

Engagement 3.15.0 is still unpublished. Default CI compiles Experiment and Session Replay only. Opt into Engagement with `-PsdkVerificationIncludeEngagement=true` once that artifact exists. Runtime Engagement tests also need a host-native QuickJS library (`-PsdkVerificationEngagementNativeLibPath`).

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

## Engagement coverage

| Test file | Customer setup | Gate |
|---|---|---:|
| `EngagementPluginIntegrationTest` | Registration before or after Analytics startup | Host-native |
| `EngagementPluginIntegrationTest` | Identity/Identify/reset propagation; opted-out startup suppresses requests | Host-native |
| `EngagementPluginIntegrationTest` | Duplicate registration keeps the first plugin | Host-native |
| `EngagementPluginIntegrationTest` | Removal and re-registration create a usable client | Host-native |
| `EngagementPluginIntegrationTest` | Operational client calls through the host accessor | Host-native |
| `EngagementPluginJavaCompatibilityTest` | Java factory and `UniversalPlugin` compatibility | Opt-in |
| `EngagementPluginNonAmplitudeHostTest` | Third-party lifecycle and event pipeline | Host-native |

## Blade combination coverage

| Test file | Customer setup | Gate |
|---|---|---:|
| `BladeCombinationNonAmplitudeHostTest` | Experiment + Session Replay in both registration orders | Yes |
| `BladeCombinationNonAmplitudeHostTest` | Experiment exposure is enriched with the Session Replay ID | Yes |
| `AllBladesNonAmplitudeHostTest` | Experiment + Session Replay + Engagement on one third-party host | Host-native |
| `AllBladesNonAmplitudeHostTest` | Removing one blade leaves the remaining accessors usable | Host-native |

## Unified Wrapper next phase

| Combination | Intent |
|---|---|
| All blades enabled | One initialization exposes every configured blade |
| Any single blade disabled | Optional products do not block the remaining products |
| Repeated or concurrent initialization | One effective initialization; all callers converge |
| Multiple Experiment deployments | Keyed access stays deterministic; ambiguous default access is explicit |
| Partial initialization failure | Healthy blades remain available and the failed blade is observable |
| Existing Analytics client | Wrapper adopts caller-owned Analytics without duplicating lifecycle |
