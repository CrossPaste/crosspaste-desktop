import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.Constructor
import java.io.FileReader
import java.util.Properties

group = "com.clipevery"
version = "1.0"

repositories {
    mavenCentral()
    google()
    maven("https://jitpack.io")
    maven("https://jogamp.org/deployment/maven")
}

plugins {
    alias(libs.plugins.conveyor)
    alias(libs.plugins.jetbrainsCompose)
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.realmKotlin)
    alias(libs.plugins.download)
}

buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        classpath(libs.yaml)
    }
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
            implementation(libs.ktor.server.compression)
            implementation(libs.ktor.server.content.negotiation)
            implementation(libs.ktor.server.core)
            implementation(libs.ktor.server.netty)
            implementation(libs.ktor.server.status.pages)
            implementation(libs.logback.classic)
            implementation(libs.selenium.chrome.driver)
            implementation(libs.signal.client)
            implementation(libs.system.tray)
            implementation(libs.theme.detector)
            implementation(libs.webp.imageio)
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

        configurations {
            all {
                exclude(group = "io.opentelemetry")
                exclude(group = "io.opentelemetry.semconv")
                exclude(group = "net.java.dev.jna", module = "jna-jpms")
                exclude(group = "net.java.dev.jna", module = "jna-platform-jpms")
                exclude(group = "org.seleniumhq.selenium", module = "selenium-firefox-driver")
                exclude(group = "org.seleniumhq.selenium", module = "selenium-edge-driver")
                exclude(group = "org.seleniumhq.selenium", module = "selenium-ie-driver")
            }
        }
    }
}

dependencies {
    // Use the configurations created by the Conveyor plugin to tell Gradle/Conveyor where to find the artifacts for each platform.
    macAarch64(compose.desktop.macos_arm64)
    windowsAmd64(compose.desktop.windows_x64)
}

compose.desktop {
    application {

        val os: OperatingSystem = DefaultNativePlatform.getCurrentOperatingSystem()

        buildTypes.release.proguard {
            configurationFiles.from("compose-desktop.pro")
        }

        mainClass = "com.clipevery.Clipevery"

        jvmArgs("--add-opens", "java.desktop/sun.awt=ALL-UNNAMED")
        jvmArgs("--add-opens", "java.desktop/java.awt.peer=ALL-UNNAMED")

        if (os.isMacOsX) {

            tasks.register<Exec>("compileSwift") {
                group = "build"
                description = "Compile Swift code and output the dylib to the build directory."

                // 定义编译命令
                commandLine(
                    "swiftc",
                    "-emit-library",
                    "src/desktopMain/swift/MacosApi.swift",
                    "-target",
                    "x86_64-apple-macos10.15",
                    "-o",
                    layout.buildDirectory.file("classes/kotlin/desktop/main/darwin-x86-64/libMacosApi.dylib").get().asFile.absolutePath,
                )

                outputs.file(layout.buildDirectory.file("classes/kotlin/desktop/main/darwin-x86-64/libMacosApi.dylib").get().asFile)
            }

            tasks.named("desktopJar") {
                dependsOn("compileSwift")
            }
            tasks.named("desktopTest") {
                dependsOn("compileSwift")
            }

            jvmArgs("--add-opens", "java.desktop/sun.lwawt=ALL-UNNAMED")
            jvmArgs("--add-opens", "java.desktop/sun.lwawt.macosx=ALL-UNNAMED")
        }

        val loggerLevel = project.findProperty("loggerLevel")?.toString() ?: "info"
        val appEnv = project.findProperty("appEnv")?.toString() ?: "DEVELOPMENT"
        val globalListener = project.findProperty("globalListener")?.toString() ?: "true"

        jvmArgs("-DloggerLevel=$loggerLevel")
        jvmArgs("-DappEnv=$appEnv")
        jvmArgs("-DglobalListener=$globalListener")
        jvmArgs("-Dcompose.interop.blending=true")
        jvmArgs("-Dio.netty.maxDirectMemory=268435456")
        jvmArgs("-DloggerDebugPackages=com.clipevery.routing,com.clipevery.net.clientapi")

        nativeDistributions {

            appResourcesRootDir = project.layout.projectDirectory.dir("resources")
            packageName = "clipevery"
            packageVersion = "1.0.0"

            // If we want to use arthas attach application in production environment,
            // we need to use
            // includeAllModules = true
            modules("jdk.charsets", "java.net.http")

            val jbrYamlFile = project.projectDir.toPath().resolve("jbr.yaml").toFile()
            val jbrReleases = loadJbrReleases(jbrYamlFile)
            val jbrDir = project.projectDir.resolve("jbr")
            if (!jbrDir.exists()) {
                jbrDir.mkdirs()
            }

            val webDriverProperties = Properties()
            val webDriverFile = project.projectDir.toPath().resolve("webDriver.properties").toFile()
            webDriverProperties.load(FileReader(webDriverFile))

            if (os.isMacOsX) {
                targetFormats(TargetFormat.Dmg)

                macOS {
                    iconFile = project.projectDir.toPath().parent.resolve("icon/clipevery.icns").toFile()
                    bundleID = "com.clipevery.mac"
                    appCategory = "public.app-category.utilities"
                    infoPlist {
                        dockName = "Clipevery"
                        extraKeysRawXml = """
                        <key>LSUIElement</key>
                        <string>true</string>
                        <key>LSMinimumSystemVersion</key>
                        <string>10.15.0</string>
                    """
                    }

                    jvmArgs("-Dmac.bundleID=$bundleID")

                    val process = Runtime.getRuntime().exec("uname -m")
                    val result = process.inputStream.bufferedReader().use { it.readText() }.trim()

                    when (result) {
                        "x86_64" -> {
                            getJbrReleases(
                                "osx-x64",
                                jbrReleases,
                                jbrDir,
                            )
                            getChromeDriver(
                                "mac-x64",
                                webDriverProperties,
                                appResourcesRootDir
                                    .get()
                                    .dir("macos-x64"),
                            )
                        }

                        "arm64" -> {
                            getJbrReleases(
                                "osx-aarch64",
                                jbrReleases,
                                jbrDir,
                            )
                            getChromeDriver(
                                "mac-arm64",
                                webDriverProperties,
                                appResourcesRootDir
                                    .get()
                                    .dir("macos-arm64"),
                            )
                        }
                    }
                }
            }

            if (os.isWindows) {
                windows {
                    targetFormats(TargetFormat.Msi)

                    val architecture = System.getProperty("os.arch")

                    if (architecture.contains("64")) {
                        getJbrReleases(
                            "windows-x64",
                            jbrReleases,
                            jbrDir,
                        )
                        getChromeDriver(
                            "win64",
                            webDriverProperties,
                            appResourcesRootDir
                                .get()
                                .dir("windows-x64"),
                        )
                    } else {
                        throw IllegalArgumentException("Unsupported architecture: $architecture")
                    }

                    iconFile = file("src/desktopMain/resources/icons/clipevery.ico")
                }
            }

            if (os.isLinux) {
                linux {
                    targetFormats(TargetFormat.Deb)

                    getJbrReleases(
                        "linux-x64",
                        jbrReleases,
                        jbrDir,
                    )
                    getChromeDriver(
                        "linux64",
                        webDriverProperties,
                        appResourcesRootDir
                            .get()
                            .dir("linux-x64"),
                    )
                }
            }
        }
    }
}

// region Work around temporary Compose bugs.
configurations.all {
    attributes {
        // https://github.com/JetBrains/compose-jb/issues/1404#issuecomment-1146894731
        attribute(Attribute.of("ui", String::class.java), "awt")
    }
}

fun getJbrReleases(
    platform: String,
    jbrReleases: JbrReleases,
    downDir: File,
) {
    val jbrDetails = jbrReleases.jbr[platform]!!
    val fileName = jbrDetails.url.substringAfterLast("/")
    downJbrReleases(jbrDetails.url, downDir) {
        !downDir.resolve(fileName).exists()
    }
}

fun downJbrReleases(
    url: String,
    downDir: File,
    checkExist: () -> Boolean,
) {
    if (checkExist()) {
        download.run {
            src { url }
            dest { downDir }
            overwrite(true)
            tempAndMove(true)
        }
    }
}

fun getChromeDriver(
    driverOsArch: String,
    properties: Properties,
    downDir: Directory,
) {
    val chromeDriver = "chromedriver-$driverOsArch"
    val chromeHeadlessShell = "chrome-headless-shell-$driverOsArch"

    downloadChromeDriver(chromeDriver, properties.getProperty(chromeDriver)!!, downDir) {
        downDir.dir(chromeDriver).asFileTree.isEmpty
    }
    downloadChromeDriver(chromeHeadlessShell, properties.getProperty(chromeHeadlessShell)!!, downDir) {
        downDir.dir(chromeHeadlessShell).asFileTree.isEmpty
    }
}

fun downloadChromeDriver(
    name: String,
    url: String,
    downDir: Directory,
    checkExist: () -> Boolean,
) {
    if (checkExist()) {
        download.run {
            src { url }
            dest { downDir }
            overwrite(true)
            tempAndMove(true)
        }
        copy {
            from(zipTree(downDir.file("$name.zip")))
            into(downDir)
        }
        delete(downDir.file("$name.zip"))
    }
}

fun loadJbrReleases(file: File): JbrReleases {
    val yaml = Yaml(Constructor(JbrReleases::class.java, LoaderOptions()))
    file.inputStream().use {
        val jbrReleases = yaml.load<JbrReleases>(it)
        return jbrReleases
    }
}

data class JbrReleases(
    var jbr: Map<String, JbrDetails> = mutableMapOf(),
)

data class JbrDetails(
    var url: String = "",
    var sha512: String = "",
)
