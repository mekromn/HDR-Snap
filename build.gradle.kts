buildscript {
    dependencies {
        // AGP 9 provides Kotlin compilation itself. Put the matching modern KGP
        // on the build classpath without applying kotlin-android so the Compose
        // compiler plugin and AGP built-in Kotlin use the same toolchain.
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.3.21")
    }
}

plugins {
    id("com.android.application") version "9.3.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.21" apply false
}
