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
    val hostOs = System.getProperty("os.name")
    val isArm64 = System.getProperty("os.arch") == "aarch64"
    val isMingwX64 = hostOs.startsWith("Windows")

    val cliTarget = project.findProperty("cli.target") as? String
    val isMacosTarget =
        when (cliTarget) {
            "macosArm64", "macosX64" -> true
            null -> hostOs == "Mac OS X"
            else -> false
        }

    val nativeTarget =
        if (cliTarget != null) {
            when (cliTarget) {
                "macosArm64" -> macosArm64("native")
                "macosX64" -> macosX64("native")
                "linuxArm64" -> linuxArm64("native")
                "linuxX64" -> linuxX64("native")
                "mingwX64" -> mingwX64("native")
                else -> throw GradleException("Unsupported CLI target: $cliTarget")
            }
        } else {
            when {
                hostOs == "Mac OS X" && isArm64 -> macosArm64("native")
                hostOs == "Mac OS X" && !isArm64 -> macosX64("native")
                hostOs == "Linux" && isArm64 -> linuxArm64("native")
                hostOs == "Linux" && !isArm64 -> linuxX64("native")
                isMingwX64 -> mingwX64("native")
                else -> throw GradleException("Host OS is not supported in Kotlin/Native.")
            }
        }

    nativeTarget.apply {
        compilations.getByName("main") {
            cinterops {
                val execpath by creating
            }
        }
        binaries {
            executable {
                entryPoint = "com.crosspaste.cli.main"
                baseName = "crosspaste-cli"
                // Workaround for Clikt duplicate symbol bug in Kotlin/Native
                // See: https://github.com/ajalt/clikt/issues/598
                // Apple ld does not support --allow-multiple-definition (GNU ld only)
                if (!isMacosTarget) {
                    linkerOpts("--allow-multiple-definition")
                }
            }
        }
    }

    sourceSets {
        val nativeMain by getting {
            dependencies {
                implementation(libs.clikt)
                implementation(libs.koin.core)
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.ktor.client.core)
                implementation(libs.ktor.client.cio)
                implementation(libs.ktor.client.content.negotiation)
                implementation(libs.ktor.serialization.kotlinx.json)
                implementation(libs.okio)
            }
        }
    }
}
