plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.utctaptimer.app"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.utctaptimer.app"
        minSdk = 30
        targetSdk = 35
        versionCode = 2
        versionName = "2.0-smart"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    implementation("com.google.mlkit:text-recognition:16.0.1")
    testImplementation("junit:junit:4.13.2")
}
