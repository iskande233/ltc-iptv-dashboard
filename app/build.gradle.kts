plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.latchi.admin"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.latchi.admin"
        minSdk = 24
        targetSdk = 34
        versionCode = 3
        versionName = "2.1.0 VIP"
    }

    signingConfigs {
        create("debug_signed") {
            storeFile = file("${rootProject.projectDir}/../latchi.jks")
                .takeIf { it.exists() }
                ?: file("${System.getProperty("user.home")}/.android/debug.keystore")
            storePassword = "latchi2026"
            keyAlias = "latchi"
            keyPassword = "latchi2026"
            enableV1Signing = true
            enableV2Signing = true
            enableV3Signing = true
        }
    }
    buildTypes {
        getByName("debug") {
            try {
                signingConfig = signingConfigs.getByName("debug_signed")
            } catch (_: Exception) {}
        }
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
}
