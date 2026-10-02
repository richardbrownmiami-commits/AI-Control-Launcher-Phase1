plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.aicontrol.launcher"
    compileSdk = 35
    ndkVersion = "27.0.12077973"
    defaultConfig {
        applicationId = "com.aicontrol.launcher"
        minSdk = 28
        targetSdk = 35
        versionCode = 3
        versionName = "0.3.0-launcher"
        ndk { abiFilters += listOf("armeabi-v7a") }
        externalNativeBuild { cmake { arguments += listOf("-DANDROID_PLATFORM=android-28") } }
    }
    externalNativeBuild { cmake { path = file("src/main/cpp/CMakeLists.txt"); version = "3.31.6" } }

    lint {
        disable += "ExpiredTargetSdkVersion"
    }

    buildTypes {
        release { isMinifyEnabled = false; isDebuggable = false; signingConfig = signingConfigs.getByName("debug") }
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
