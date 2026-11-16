# Unified consumer

This standalone AGP 8.13 / Kotlin 2.2 app depends only on Unified's Maven coordinate plus the Kotlin standard library. Its manifest-reachable Activity calls Analytics, Experiment, and Replay APIs. The release build runs R8 and resource shrinking with the SDKs' packaged consumer rules and the standard Android rules. There are no app dependency workarounds or custom keep rules.

## Check the checkout

Use JDK 21 for SDK publication, JDK 17 for the consumer, and Android SDK platform 36. No device or emulator is needed.

From the repository root, publish checkout candidates into an isolated repository. Use Unified's pinned Analytics version for all three candidates so Unified's published dependencies select the checkout without app-side constraints. These versions are local to this repository; this does not publish a release.

```bash
verification_repo="$PWD/build/consumer-maven"
candidate="$(sed -n 's/^analyticsAndroid = "\(.*\)"/\1/p' unified/gradle/libs.versions.toml)"
./gradlew :analytics-core:publishToMavenLocal :android:publishToMavenLocal :unified:publishToMavenLocal \
  -PsignAllPublications=false -PVERSION_NAME="$candidate" -Dmaven.repo.local="$verification_repo"
```

Then select JDK 17 and run the consumer's wrapper:

```bash
cd sdk-verification/consumer
./gradlew :app:assembleRelease \
  -PunifiedVersion="$candidate" -PcandidateRepository="$verification_repo"
```

The APK is written to `app/build/outputs/apk/release/app-release.apk`. The candidate repository is exclusive for Unified, Analytics Android, and core. Missing candidates fail without Maven Central fallback. Without `candidateRepository`, the consumer resolves released artifacts from Google and Maven Central; `unifiedVersion` defaults to `unified/gradle.properties`.

## Coverage and limits

The PR CI check verifies published dependency metadata, consumer compilation, and R8. The independent wrapper and toolchain catch failures hidden by the JVM contract tests. It does not install or run the APK. Replay capture, uploads, and device lifecycle behavior remain deferred.

Unified exposes coroutines because its pinned legacy Replay client exposes `CoroutineScope`. Reassess that workaround when Unified adopts Compose SR. Unified also selects OkHttp 4.12 at runtime for its AGP-compatible consumer rules. Removing the coroutines fix should fail consumer compilation; removing the OkHttp fix should fail R8. Restore and republish each fix before checking the next one.
