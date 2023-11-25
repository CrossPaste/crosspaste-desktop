import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.compose.ExperimentalComposeLibrary

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.jetbrainsCompose)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    jvm("desktop")
    
    sourceSets {
        val desktopMain by getting
        
        desktopMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation("net.java.dev.jna:jna:5.13.0")
            implementation("net.java.dev.jna:jna-platform:5.13.0")
            implementation("com.google.zxing:core:3.5.2")
            implementation("com.google.zxing:javase:3.5.2")
            implementation("ch.qos.logback:logback-classic:1.4.11")
            implementation("io.javalin:javalin:5.6.3")
            implementation("org.signal:libsignal-client:0.35.0")
            implementation("br.com.devsrsouza.compose.icons:tabler-icons-desktop:1.1.0")
            implementation("io.insert-koin:koin-core:3.5.0")
        }
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material)
            @OptIn(ExperimentalComposeLibrary::class)
            implementation(compose.components.resources)
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.1")
            implementation("io.github.oshai:kotlin-logging-jvm:5.1.0")
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
        }
    }
}
