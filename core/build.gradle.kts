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

    js(IR) {
        browser()
        useEsModules()
        binaries.library()
        generateTypeScriptDefinitions()
    }

    val hostOs = System.getProperty("os.name")
    val isArm64 = System.getProperty("os.arch") == "aarch64"
    val isMingwX64 = hostOs.startsWith("Windows")

    val cliTarget = project.findProperty("cli.target") as? String

    if (cliTarget != null) {
        when (cliTarget) {
            "macosArm64" -> macosArm64("nativeApp")
            "macosX64" -> macosX64("nativeApp")
            "linuxArm64" -> linuxArm64("nativeApp")
            "linuxX64" -> linuxX64("nativeApp")
            "mingwX64" -> mingwX64("nativeApp")
            else -> throw GradleException("Unsupported CLI target: $cliTarget")
        }
    } else {
        when {
            hostOs == "Mac OS X" && isArm64 -> macosArm64("nativeApp")
            hostOs == "Mac OS X" && !isArm64 -> macosX64("nativeApp")
            hostOs == "Linux" && isArm64 -> linuxArm64("nativeApp")
            hostOs == "Linux" && !isArm64 -> linuxX64("nativeApp")
            isMingwX64 -> mingwX64("nativeApp")
            else -> throw GradleException("Host OS is not supported in Kotlin/Native.")
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.cryptography.core)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlin.logging)
        }

        val desktopMain by getting {
            dependencies {
                implementation(libs.cryptography.provider.jdk)
            }
        }

        val jsMain by getting {
            dependencies {
                implementation(libs.cryptography.provider.webcrypto)
            }
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}
