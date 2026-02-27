import java.io.FileReader
import java.util.Properties

val versionProperties = Properties()
versionProperties.load(
    FileReader(
        project.projectDir
            .toPath()
            .parent
            .resolve("app/src/desktopMain/resources/crosspaste-version.properties")
            .toFile(),
    ),
)

group = "com.crosspaste"
version = versionProperties.getProperty("version")

plugins {
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.jetbrainsCompose)
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.ktlint)
}

ktlint {
    verbose = true
    android = false
    ignoreFailures = false
}

kotlin {
    jvm("desktop")

    sourceSets {
        commonMain.dependencies {
            implementation(project(":shared"))
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.runtime)
            implementation(libs.compose.ui)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
        }

        val desktopMain by getting {
            dependencies {
            }
        }
    }
}
