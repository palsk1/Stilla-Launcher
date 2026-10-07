import com.github.triplet.gradle.androidpublisher.ResolutionStrategy
import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    // Uploads release bundles to Google Play (3.x: 4.x needs AGP 9).
    id("com.github.triplet.play") version "3.13.0"
}

android {
    namespace = "se.stilla.launcher"
    compileSdk = 36

    defaultConfig {
        applicationId = "se.stilla.launcher"
        minSdk = 29
        targetSdk = 36
        versionCode = 6
        versionName = "0.3.0"
    }

    // The Google Play upload key lives next to the project (never in git):
    // keystore.properties + upload-key.jks. When those are missing (GitHub Actions), the same
    // values come from environment variables filled from GitHub secrets. Without either,
    // release falls back to the debug key.
    val keyProps = Properties().apply {
        val file = rootProject.file("keystore.properties")
        if (file.exists()) {
            file.inputStream().use(::load)
        } else {
            val env = mapOf(
                "storeFile" to "STILLA_KEYSTORE_FILE",
                "storePassword" to "STILLA_KEYSTORE_PASSWORD",
                "keyAlias" to "STILLA_KEY_ALIAS",
                "keyPassword" to "STILLA_KEY_PASSWORD",
            ).mapValues { System.getenv(it.value).orEmpty() }
            if (env.values.all { it.isNotBlank() }) putAll(env)
        }
    }
    signingConfigs {
        if (keyProps.isNotEmpty()) {
            create("upload") {
                // An absolute path (CI) is used as is; a relative one is next to the project.
                storeFile = rootProject.file(keyProps.getProperty("storeFile"))
                storePassword = keyProps.getProperty("storePassword")
                keyAlias = keyProps.getProperty("keyAlias")
                keyPassword = keyProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("upload") ?: signingConfigs.getByName("debug")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

// Google Play: `publishReleaseBundle` builds and uploads to internal testing.
// Needs play-service-account.json next to the project (never in git). When it's missing
// (GitHub Actions), the plugin reads the same JSON from ANDROID_PUBLISHER_CREDENTIALS.
// Without either, publishing is switched off.
val playKey = rootProject.file("play-service-account.json")
play {
    enabled.set(playKey.exists() || !System.getenv("ANDROID_PUBLISHER_CREDENTIALS").isNullOrBlank())
    if (playKey.exists()) serviceAccountCredentials.set(playKey)
    track.set("internal")
    defaultToAppBundles.set(true)
    // Version code = highest on Play + 1, so every upload just works.
    resolutionStrategy.set(ResolutionStrategy.AUTO)
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":engine"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
