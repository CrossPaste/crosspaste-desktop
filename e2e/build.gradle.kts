import java.util.Properties

val versionProperties = Properties()
project.projectDir
    .toPath()
    .parent
    .resolve("app/src/desktopMain/resources/crosspaste-version.properties")
    .toFile()
    .reader()
    .use { versionProperties.load(it) }

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
    jvm("desktop") {}

    sourceSets {
        val desktopMain by getting {
            dependencies {
                implementation(project(":app"))
                implementation(project(":shared"))
                implementation(project(":core"))
                implementation(libs.clikt)
                implementation(libs.cryptography.core)
                implementation(libs.cryptography.provider.jdk)
                implementation(libs.jmdns)
                implementation(libs.kotlin.logging)
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.ktor.client.cio)
                implementation(libs.ktor.client.content.negotiation)
                implementation(libs.ktor.client.core)
                implementation(libs.ktor.client.logging)
                implementation(libs.ktor.serialization.kotlinx.json)
                implementation(libs.logback.classic)
            }
        }

        configurations.configureEach {
            exclude(group = "io.opentelemetry")
            exclude(group = "io.opentelemetry.semconv")
            exclude(group = "net.java.dev.jna", module = "jna-jpms")
            exclude(group = "net.java.dev.jna", module = "jna-platform-jpms")
        }
    }
}

// Match :app's `ui=awt` attribute so depending on it resolves the correct variant.
configurations.all {
    if (isCanBeResolved || isCanBeConsumed) {
        attributes {
            attribute(Attribute.of("ui", String::class.java), "awt")
        }
    }
}

tasks.register<JavaExec>("run") {
    group = "application"
    description = "Run the CrossPaste e2e harness against a target device."
    mainClass.set("com.crosspaste.e2e.cli.MainKt")
    val desktopMain =
        kotlin.targets
            .getByName("desktop")
            .compilations
            .getByName("main")
    val runtimeFiles = desktopMain.runtimeDependencyFiles ?: project.files()
    classpath = runtimeFiles + desktopMain.output.allOutputs
    standardInput = System.`in`
    jvmArgs(
        "-Djava.net.preferIPv4Stack=true",
        "-Djava.net.preferIPv6Addresses=false",
    )
}
