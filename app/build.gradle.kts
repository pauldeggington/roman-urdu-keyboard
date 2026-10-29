plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
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
        // optional GIPHY key baked into the release by CI (secret GIPHY_API_KEY); people can also paste their own in the app
        buildConfigField("String", "GIPHY_KEY", "\"" + (System.getenv("GIPHY_API_KEY") ?: "").trim() + "\"")
        manifestPlaceholders["previewExported"] = project.hasProperty("noNative").toString()
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
            // -PtrialR8 builds an experimental smaller/faster copy (see proguard-rules.pro); the normal build stays unminified
            isMinifyEnabled = project.hasProperty("trialR8")
            isShrinkResources = project.hasProperty("trialR8")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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
                output.outputFileName = "RomanUrduKeyboard_${System.getenv("VERSION_NAME") ?: "dev"}-${variant.buildType}${if (project.hasProperty("trialR8")) "-r8" else ""}.apk"
            }
        }
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    testOptions {
        unitTests.all { it.testLogging { showStandardStreams = true } }
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
    // the plain-Kotlin logic: suggestions, glide, detection, translation prompts
    implementation(project(":core"))
    // on-device Gemma via LiteRT-LM
    implementation(libs.litertlm)
    implementation(libs.androidx.core)
    implementation(libs.androidx.recyclerview)

    // Jetpack Compose: the setup guide and settings screens (the keyboard itself stays on Canvas views)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.activity.compose)

    testImplementation(libs.junit)
}
