# RemoteConfigClient holds subscriber callbacks only through WeakReferences, so each subscriber keeps
# its callback alive with a strong reference in a field. R8 does not count that as a use and may
# remove the field, after which the callback can be collected and remote config updates are dropped.
# Keep fields declared with the callback type; renaming is still allowed.
#
# R8 ignores META-INF/proguard/ when this JAR also has rules under META-INF/com.android.tools/,
# so this rule has to be copied there if targeted rules are ever added.
-keepclassmembers,allowobfuscation class com.amplitude.** {
    com.amplitude.core.remoteconfig.RemoteConfigClient$RemoteConfigCallback *;
}
