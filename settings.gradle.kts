rootProject.name = "crosspaste-desktop"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
        maven("https://packages.jetbrains.team/maven/p/ij/intellij-dependencies")
    }
}

dependencyResolutionManagement {
    @Suppress("UnstableApiUsage")
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
        maven("https://jogamp.org/deployment/maven")
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
        maven("https://packages.jetbrains.team/maven/p/ij/intellij-dependencies")
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

include(":app")
include(":cli")
include(":shared")