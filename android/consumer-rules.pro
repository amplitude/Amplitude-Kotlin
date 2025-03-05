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