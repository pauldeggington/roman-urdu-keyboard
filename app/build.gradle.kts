plugins {
    id("com.android.application")
}

android {
    namespace = "com.paul.urdukb"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.paul.urdukb"
        minSdk = 31
        targetSdk = 37
        // CI sets these so every release installs over the last one
        versionCode = System.getenv("VERSION_CODE")?.toIntOrNull() ?: 5001
        versionName = System.getenv("VERSION_NAME") ?: "2.0-dev"
        // emulator preview builds (-PnoNative) carry test hooks so CI can drive the keyboard for screenshots
        buildConfigField("boolean", "PREVIEW", project.hasProperty("noNative").toString())
        ndk {
            abiFilters.clear()
            abiFilters.addAll(listOf("arm64-v8a"))
        }
    }

    // Optional fixed signing key, supplied by CI from GitHub secrets.
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
            // This is the build that gets published: not debuggable, not minified (keeps LiteRT-LM's JNI classes intact).
            isMinifyEnabled = false
            isJniDebuggable = false
            isDebuggable = false
            signingConfigs.findByName("pinned")?.let { signingConfig = it }
        }
        release {
            isMinifyEnabled = false
        }
    }

    androidComponents.onVariants { variant ->
        variant.outputs.forEach { output ->
            if (output is com.android.build.api.variant.impl.VariantOutputImpl) {
                output.outputFileName = "RomanUrduKeyboard_${System.getenv("VERSION_NAME") ?: "dev"}-${variant.buildType}.apk"
            }
        }
    }

    buildFeatures {
        buildConfig = true
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
            // emulator previews are built without native code so they install on any CPU type
            if (project.hasProperty("noNative")) excludes += "**/*.so"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    lint {
        abortOnError = false
    }
}

dependencies {
    // on-device Gemma via LiteRT-LM
    implementation("com.google.ai.edge.litertlm:litertlm-android:0.18.1")
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.recyclerview:recyclerview:1.4.0")
}
