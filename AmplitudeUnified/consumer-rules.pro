# We rely on these Google Play services AppSet classes when useAppSetIdForDeviceId = true
-keep class com.google.android.gms.appset.AppSet {
  public getClient(android.content.Context);
}
-keep class com.google.android.gms.appset.AppSetIdClient {
  public getAppSetIdInfo();
}
-keep class com.google.android.gms.appset.AppSetIdInfo {
  public getId();
}
-keep class com.google.android.gms.tasks.Tasks {
  public await(com.google.android.gms.tasks.Task);
}
-keep class com.google.android.gms.tasks.Task

#################### START: Compose Proguard Rules ####################

# The Android SDK checks at runtime if these classes are available via Class.forName
-keepnames interface androidx.compose.ui.node.Owner
-keep class com.amplitude.android.internal.locators.ComposeViewTargetLocator

-keepnames class androidx.compose.foundation.ClickableElement
-keepnames class androidx.compose.foundation.CombinedClickableElement

#################### END: Compose Proguard Rules ####################