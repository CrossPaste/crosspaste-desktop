import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import java.io.FileReader
import java.util.Properties

repositories {
    mavenCentral()
    google()
    maven("https://jitpack.io")
    maven("https://jogamp.org/deployment/maven")
}

plugins {
    alias(libs.plugins.jetbrainsCompose)
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.realmKotlin)
    alias(libs.plugins.download)
}

ktlint {
    verbose = true
    android = false
    ignoreFailures = true
    filter {
        exclude("**/build/**")
        exclude("**/src/*Test/**")
        exclude("**/src/*Main/kotlin/androidx/**")
        include("**/src/**/*.kt")
    }
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
            implementation(libs.selenium.java)
            implementation(libs.signal.client)
            implementation(libs.theme.detector)
            implementation(libs.zxing.core)
            implementation(libs.zxing.javase)
        }
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
            implementation(libs.kotlin.logging)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.coroutines.swing)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.material.desktop)
            implementation(libs.realm.kotlin.base)
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

        val os: OperatingSystem = DefaultNativePlatform.getCurrentOperatingSystem()

        buildTypes.release.proguard {
            configurationFiles.from("compose-desktop.pro")
        }

        mainClass = "com.clipevery.MainKt"

        jvmArgs("--add-opens", "java.desktop/sun.awt=ALL-UNNAMED")
        jvmArgs("--add-opens", "java.desktop/java.awt.peer=ALL-UNNAMED")

        if (os.isMacOsX) {
            jvmArgs("--add-opens", "java.desktop/sun.lwawt=ALL-UNNAMED")
            jvmArgs("--add-opens", "java.desktop/sun.lwawt.macosx=ALL-UNNAMED")
        }

        val loggerLevel = project.findProperty("loggerLevel")?.toString() ?: "info"
        val appEnv = project.findProperty("appEnv")?.toString() ?: "DEVELOPMENT"

        jvmArgs("-DloggerLevel=$loggerLevel")
        jvmArgs("-DappEnv=$appEnv")
        jvmArgs("-DsupportShortcutKey=true")
        jvmArgs("-Dcompose.interop.blending=true")
        jvmArgs("-Dio.netty.maxDirectMemory=268435456")
        jvmArgs("-DloggerDebugPackages=com.clipevery.routing,com.clipevery.net.clientapi")

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)

            appResourcesRootDir = project.layout.projectDirectory.dir("resources")
            packageName = "Clipevery"
            packageVersion = "1.0.0"

            modules("jdk.charsets")

            val properties = Properties()
            val webDriverFile = project.projectDir.toPath().resolve("webDriver.properties").toFile()
            properties.load(FileReader(webDriverFile))

            macOS {
                if (os.isMacOsX) {
                    val process = Runtime.getRuntime().exec("uname -m")
                    val result = process.inputStream.bufferedReader().use { it.readText() }.trim()

                    when (result) {
                        "x86_64" -> getChromeDriver("mac-x64", properties, appResourcesRootDir.get().dir("macos-x64"))
                        "arm64" ->
                            getChromeDriver(
                                "mac-arm64",
                                properties,
                                appResourcesRootDir
                                    .get()
                                    .dir("macos-arm64"),
                            )
                    }
                }

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

                val architecture = System.getProperty("os.arch")

                if (architecture.contains("64")) {
                    getChromeDriver("win64", properties, appResourcesRootDir.get().dir("windows-x64"))
                } else {
                    getChromeDriver("win32", properties, appResourcesRootDir.get().dir("windows-x86"))
                }

                iconFile = file("src/desktopMain/resources/icons/clipevery.ico")
            }
        }
    }
}

fun getChromeDriver(
    driverOsArch: String,
    properties: Properties,
    resourceDir: Directory,
) {
    val chromeDriver = "chromedriver-$driverOsArch"
    val chromeHeadlessShell = "chrome-headless-shell-$driverOsArch"

    download(chromeDriver, properties, resourceDir)
    download(chromeHeadlessShell, properties, resourceDir)
}

fun download(
    name: String,
    properties: Properties,
    resourceDir: Directory,
) {
    if (resourceDir.dir(name).asFileTree.isEmpty) {
        val chromeHeadlessShellUrl = properties.getProperty(name)!!
        download.run {
            src { chromeHeadlessShellUrl }
            dest { resourceDir }
            overwrite(true)
            tempAndMove(true)
        }
        copy {
            from(zipTree(resourceDir.file("$name.zip")))
            into(resourceDir)
        }
        delete(resourceDir.file("$name.zip"))
    }
}
