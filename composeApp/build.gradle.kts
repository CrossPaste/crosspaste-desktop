import org.jetbrains.compose.desktop.application.dsl.TargetFormat

repositories {
    mavenCentral()
    google()
    maven("https://jitpack.io")
    maven("https://jogamp.org/deployment/maven")
}

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.jetbrainsCompose)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.realmKotlin)
}

kotlin {
    jvm("desktop")
    
    sourceSets {
        val desktopMain by getting
        
        desktopMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.compose.shimmer)
            implementation(libs.guava)
            implementation(libs.jmdns)
            implementation(libs.jna)
            implementation(libs.jna.platform)
            implementation(libs.jnativehook)
            implementation(libs.jsoup)
            implementation(libs.koin.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.cio)
            implementation(libs.ktor.client.logging)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.ktor.server.content.negotiation)
            implementation(libs.ktor.server.core)
            implementation(libs.ktor.server.netty)
            implementation(libs.ktor.server.status.pages)
            implementation(libs.logback.classic)
            implementation(libs.signal.client)
            implementation(libs.theme.detector)
            implementation(libs.zxing.core)
            implementation(libs.zxing.javase)
        }
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material)
            implementation(compose.components.resources)
            implementation(compose.desktop.currentOs)
            implementation(libs.compose.webview.multiplatform)
            implementation(libs.kotlin.logging)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.material.desktop)
            implementation(libs.realm.kotlin.base)
            implementation(libs.tabler.icons)
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

        buildTypes.release.proguard {
            configurationFiles.from("compose-desktop.pro")
        }

        mainClass = "com.clipevery.MainKt"

        jvmArgs("--add-opens", "java.desktop/sun.awt=ALL-UNNAMED")
        jvmArgs("--add-opens", "java.desktop/java.awt.peer=ALL-UNNAMED")

        if (System.getProperty("os.name").contains("Mac")) {
            jvmArgs("--add-opens", "java.desktop/sun.lwawt=ALL-UNNAMED")
            jvmArgs("--add-opens", "java.desktop/sun.lwawt.macosx=ALL-UNNAMED")
        }

        val loggerLevel = project.findProperty("loggerLevel")?.toString() ?: "info"
        val appEnv = project.findProperty("appEnv")?.toString() ?: "DEVELOPMENT"

        jvmArgs("-DloggerLevel=$loggerLevel")
        jvmArgs("-DappEnv=$appEnv")

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
