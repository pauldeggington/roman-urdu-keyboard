// The part of the app that is plain Kotlin with no Android calls: word suggestions and autocorrect, glide decoding,
// language detection, the translation prompt logic. It builds and tests on any computer in seconds:
//   ./gradlew :core:test
plugins {
    id("org.jetbrains.kotlin.jvm")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

dependencies {
    testImplementation(libs.junit)
}

tasks.test {
    testLogging { showStandardStreams = true }
}
