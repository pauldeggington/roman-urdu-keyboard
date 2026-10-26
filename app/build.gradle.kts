import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.paul.urdukb"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.paul.urdukb"
        minSdk = 31
        targetSdk = 34
        versionCode = 2
        versionName = "0.2"
    }

    // Optional fixed signing key, supplied by CI from GitHub secrets. Without it, Gradle's random debug key is used.
    val pinnedKeystore = System.getenv("KEYSTORE_FILE")
    val pinnedPassword = System.getenv("KEYSTORE_PASSWORD")
    if (!pinnedKeystore.isNullOrEmpty() && !pinnedPassword.isNullOrEmpty() && File(pinnedKeystore).exists()) {
        signingConfigs {
            create("pinned") {
                storeFile = File(pinnedKeystore)
                storePassword = pinnedPassword
                keyAlias = "urdukb"
                keyPassword = pinnedPassword
            }
        }
    }

    buildTypes {
        debug {
            signingConfigs.findByName("pinned")?.let { signingConfig = it }
        }
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation("com.google.ai.edge.litertlm:litertlm-android:0.18.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
}
