# Consumer R8/ProGuard rules for streaming-analytics-android.
# Amplitude loads StreamingAnalyticsPlugin by name when this artifact is on the classpath.
-keep class com.amplitude.android.streaming.StreamingAnalyticsPlugin {
    public <init>();
}

