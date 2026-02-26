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
    alias(libs.plugins.sqlDelight)
}

sqldelight {
    databases {
        create("Database") {
            packageName = "com.crosspaste"
            dialect("app.cash.sqldelight:sqlite-3-25-dialect:${libs.versions.sqldelight.get()}")
        }
    }
}

ktlint {
    verbose = true
    android = false
    ignoreFailures = false
}

kotlin {
    jvm("desktop")

    val hostOs = System.getProperty("os.name")
    val isArm64 = System.getProperty("os.arch") == "aarch64"
    val isMingwX64 = hostOs.startsWith("Windows")

    when {
        hostOs == "Mac OS X" && isArm64 -> macosArm64("nativeApp")
        hostOs == "Mac OS X" && !isArm64 -> macosX64("nativeApp")
        hostOs == "Linux" && isArm64 -> linuxArm64("nativeApp")
        hostOs == "Linux" && !isArm64 -> linuxX64("nativeApp")
        isMingwX64 -> mingwX64("nativeApp")
        else -> throw GradleException("Host OS is not supported in Kotlin/Native.")
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.cryptography.core)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.uuid)
            implementation(libs.kotlin.logging)
            implementation(libs.ksoup)
            implementation(libs.ktor.io)
            implementation(libs.okio)
            api(libs.sqldelight.coroutines.extensions)
        }

        val desktopMain by getting {
            dependencies {
                implementation(libs.cryptography.provider.jdk)
                implementation(libs.sqlite.driver)
            }
        }

        val nativeAppMain by getting {
            dependencies {
                implementation(libs.sqlite.native.driver)
            }
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}
