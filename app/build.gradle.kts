import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.mekromn.hdrsnap"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.mekromn.hdrsnap"
        minSdk = 34
        targetSdk = 36
        versionCode = 3
        versionName = "0.2.1"
    }

    val stableDevKeystore = rootProject.file("ci/hdrsnap-debug.keystore")
    if (stableDevKeystore.exists()) {
        signingConfigs.getByName("debug") {
            storeFile = stableDevKeystore
            storePassword = "hdrsnapdev"
            keyAlias = "hdrsnapdebug"
            keyPassword = "hdrsnapdev"
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.04.01")
    implementation(composeBom)

    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.exifinterface:exifinterface:1.4.2")
}
