rootProject.name = "kotlin-sdk"
include("core")
project(":core").projectDir = file("core")
include("android")
project(":android").projectDir = file("android")
include("samples:kotlin-android-app")
project(":samples:kotlin-android-app").projectDir = file("samples/kotlin-android-app")
include(":AmplitudeUnified")
