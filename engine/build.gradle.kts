import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// :engine is plain Kotlin with no Android imports, so its tests run in
// milliseconds on the computer (right-click the test folder → Run Tests).
plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test.junit)
}
