plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.drishtinav.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.drishtinav.app"
        minSdk = 29
        targetSdk = 35
        versionCode = 6
        versionName = "0.6.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // Keep the .tflite model uncompressed so MediaPipe can memory-map it.
    androidResources {
        noCompress.add("tflite")
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    // AR session + Depth API (raw per-pixel distance in mm)
    implementation("com.google.ar:core:1.54.0")
    // On-device object detection (EfficientDet-Lite0, no network needed)
    implementation("com.google.mediapipe:tasks-vision:0.10.35")

    implementation("androidx.core:core-ktx:1.15.0")
    // Branded cold-start splash with the animated sonar icon.
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    // High-accuracy GPS for the navigation engine (Phase 2)
    implementation("com.google.android.gms:play-services-location:21.3.0")
}
