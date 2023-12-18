import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.compose.ExperimentalComposeLibrary
import java.net.URI

repositories {
    mavenCentral()
    maven { url = URI("https://jitpack.io") }
}

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.jetbrainsCompose)
    alias(libs.plugins.kotlinSerialization)
    id("app.cash.sqldelight") version "2.0.1"
}

sqldelight {
    databases {
        create("Database") {
            packageName.set("com.clipevery")
        }
    }
}

kotlin {
    jvm("desktop")
    
    sourceSets {
        val desktopMain by getting
        
        desktopMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation("net.java.dev.jna:jna:5.13.0")
            implementation("net.java.dev.jna:jna-platform:5.14.0")
            implementation("com.google.zxing:core:3.5.2")
            implementation("com.google.zxing:javase:3.5.2")
            implementation("ch.qos.logback:logback-classic:1.4.14")
            implementation(libs.ktor.server.core)
            implementation(libs.ktor.server.netty)
            implementation(libs.ktor.server.content.negotiation)
            implementation(libs.ktor.serialization.jackson)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.cio)
            implementation("org.signal:libsignal-client:0.37.0")
            implementation("br.com.devsrsouza.compose.icons:tabler-icons-desktop:1.1.0")
            implementation("io.insert-koin:koin-core:3.5.0")
            implementation("com.github.kwhat:jnativehook:2.2.2")
            implementation("app.cash.sqldelight:sqlite-driver:2.0.1")
            implementation("com.github.Dansoftowner:jSystemThemeDetector:3.8")
            implementation("dev.forst:ktor-openapi-generator:0.6.1")
            implementation("org.jmdns:jmdns:3.5.9")
        }
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material)
            @OptIn(ExperimentalComposeLibrary::class)
            implementation(compose.components.resources)
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")
            implementation("io.github.oshai:kotlin-logging-jvm:5.1.1")
        }

        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}


compose.desktop {
    application {
        mainClass = "com.clipevery.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            modules("jdk.charsets")

            packageName = "Clipevery"
            packageVersion = "1.0.0"
            macOS {
                iconFile = file("src/desktopMain/resources/icons/clipevery.icns")
                bundleID = "com.clipevery"
                appCategory = "public.app-category.utilities"
                infoPlist {
                    dockName = "Clipevery"
                    extraKeysRawXml = """
                        <key>LSUIElement</key>
                        <string>true</string>
                    """
                }
            }
            windows {
                iconFile = file("src/desktopMain/resources/icons/clipevery.ico")
            }
        }
    }
}
