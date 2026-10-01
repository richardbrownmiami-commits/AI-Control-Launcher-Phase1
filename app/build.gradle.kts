plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.aicontrol.launcher"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.aicontrol.launcher"
        minSdk = 30
        targetSdk = 30
        versionCode = 2
        versionName = "0.2.0-phase1"
        ndk { abiFilters += listOf("armeabi-v7a") }
    }
    buildTypes {
        release { isMinifyEnabled = false; isDebuggable = false }
        debug { isMinifyEnabled = false }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
}
