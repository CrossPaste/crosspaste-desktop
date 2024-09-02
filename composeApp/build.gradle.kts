import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.Constructor
import java.io.FileReader
import java.util.Properties
import java.util.zip.ZipFile

val versionProperties = Properties()
versionProperties.load(
    FileReader(
        project.projectDir.toPath().resolve("src/desktopMain/resources/crosspaste-version.properties").toFile(),
    ),
)
group = "com.crosspaste"
version = versionProperties.getProperty("version")

repositories {
    mavenCentral()
    google()
    maven("https://jitpack.io")
    maven("https://jogamp.org/deployment/maven")
}

plugins {
    alias(libs.plugins.compose.compiler)
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
    ignoreFailures = false
    filter {
        exclude { element ->
            val path = element.path
            path.contains("\\generated\\") || path.contains("/generated/") ||
                path.contains("\\desktopTest\\") || path.contains("/desktopTest/") ||
                path.contains("\\commonMain\\kotlin\\androidx\\") || path.contains("/commonMain/kotlin/androidx/")
        }
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
            implementation(libs.imageio.core)
            implementation(libs.imageio.jpeg)
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
            implementation(libs.selenium.devtools)
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
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.material.desktop)
            implementation(libs.murmurhash)
            implementation(libs.okio)
            implementation(libs.realm.kotlin.base)
            implementation(libs.semver)
        }

        val commonTest by getting {
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.io.mockk)
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
                exclude(group = "org.seleniumhq.selenium", module = "selenium-manager")
            }
        }
    }
}

dependencies {
    // Use the configurations created by the Conveyor plugin to tell Gradle/Conveyor where to find the artifacts for each platform.
    macAmd64(compose.desktop.macos_x64)
    macAarch64(compose.desktop.macos_arm64)
    windowsAmd64(compose.desktop.windows_x64)
    linuxAmd64(compose.desktop.linux_x64)
}

tasks.register<Copy>("copyDevProperties") {
    from("src/desktopMain/resources/development.properties.template")
    into("src/desktopMain/resources")
    rename { "development.properties" }
    onlyIf {
        !file("src/desktopMain/resources/development.properties").exists()
    }
}

tasks.named("desktopProcessResources") {
    dependsOn("copyDevProperties")
}

compose.desktop {

    val buildFullPlatform: Boolean = System.getenv("BUILD_FULL_PLATFORM")?.lowercase() == "true"

    application {

        val os: OperatingSystem = DefaultNativePlatform.getCurrentOperatingSystem()

        buildTypes.release.proguard {
            configurationFiles.from("compose-desktop.pro")
        }

        mainClass = "com.crosspaste.CrossPaste"

        if (os.isMacOsX || buildFullPlatform) {
            if (!buildFullPlatform) {
                tasks.register<Exec>("compileSwift") {
                    group = "build"
                    description = "Compile Swift code and output the dylib to the build directory."

                    val currentArch = System.getProperty("os.arch")
                    val targetArch =
                        when {
                            currentArch.contains("arm") || currentArch.contains("aarch64") -> "arm64-apple-macos11"
                            else -> "x86_64-apple-macos10.15"
                        }

                    val archDir =
                        when {
                            currentArch.contains("arm") || currentArch.contains("aarch64") -> "darwin-aarch64"
                            else -> "darwin-x86-64"
                        }

                    val inputFile =
                        layout.projectDirectory.file("src/desktopMain/swift/MacosApi.swift").asFile

                    val outputFile =
                        layout.buildDirectory.file("classes/kotlin/desktop/main/$archDir/libMacosApi.dylib")
                            .get().asFile

                    commandLine(
                        "swiftc",
                        "-emit-library",
                        inputFile.absolutePath,
                        "-target",
                        targetArch,
                        "-o",
                        outputFile.absolutePath,
                    )

                    inputs.file(inputFile)
                    outputs.file(outputFile)
                }

                tasks.named("desktopJar") {
                    dependsOn("compileSwift")
                }
                tasks.named("desktopTest") {
                    dependsOn("compileSwift")
                }
            } else {
                // If it is to build the full platform
                // then the GitHub action will prepare the dylibs files compiled under
                // the Intel and ARM architectures to the dylib directory
                tasks.register<Copy>("copyDylibs") {
                    from("dylib/")
                    into(layout.buildDirectory.file("classes/kotlin/desktop/main"))
                }
                tasks.named("desktopJar") {
                    dependsOn("copyDylibs")
                }
                tasks.named("desktopTest") {
                    dependsOn("copyDylibs")
                }
            }
        }

        nativeDistributions {

            appResourcesRootDir = project.layout.projectDirectory.dir("resources")
            packageName = "crosspaste"
            packageVersion = version.toString()

            // If we want to use arthas attach application in production environment,
            // we need to use
            // includeAllModules = true
            modules("jdk.charsets", "java.net.http")

            // Open modules required for all platforms
            jvmArgs("--add-opens", "java.desktop/sun.awt=ALL-UNNAMED")
            jvmArgs("--add-opens", "java.desktop/java.awt.peer=ALL-UNNAMED")

            // Add system properties that need to be set for all platforms
            val loggerLevel = project.findProperty("loggerLevel")?.toString() ?: "info"
            val appEnv = project.findProperty("appEnv")?.toString() ?: "DEVELOPMENT"
            val globalListener = project.findProperty("globalListener")?.toString() ?: "true"

            jvmArgs("-DloggerLevel=$loggerLevel")
            jvmArgs("-DappEnv=$appEnv")
            jvmArgs("-Djava.net.preferIPv4Stack=true")
            jvmArgs("-Djava.net.preferIPv6Addresses=false")
            jvmArgs("-DglobalListener=$globalListener")
            jvmArgs("-Dio.netty.maxDirectMemory=268435456")
            jvmArgs("-DloggerDebugPackages=com.crosspaste.routing,com.crosspaste.net.clientapi,com.crosspaste.net.plugin")

            if (appEnv != "DEVELOPMENT") {
                tasks.withType<Jar> {
                    exclude("development.properties**")
                }
            }

            val seleniumManagerJar: File =
                configurations.detachedConfiguration(dependencies.create("org.seleniumhq.selenium:selenium-manager:4.24.0"))
                    .resolve().first()

            extract(seleniumManagerJar, appResourcesRootDir.get().asFile)

            // Add download info of jbr on all platforms
            val jbrYamlFile = project.projectDir.toPath().resolve("jbr.yaml").toFile()
            val jbrReleases = loadJbrReleases(jbrYamlFile)
            val jbrDir = project.projectDir.resolve("jbr")
            if (!jbrDir.exists()) {
                jbrDir.mkdirs()
            }

            if (os.isMacOsX || buildFullPlatform) {
                targetFormats(TargetFormat.Dmg)

                macOS {
                    bundleID = "com.crosspaste.mac"
                    appCategory = "public.app-category.utilities"
                    infoPlist {
                        dockName = "CrossPaste"
                        extraKeysRawXml = """
                        <key>LSUIElement</key>
                        <string>true</string>
                        <key>NSAccessibilityUsageDescription</key>
                        <string>This application needs accessibility permissions to enhance your interaction with the system.</string>
                        <key>LSMinimumSystemVersion</key>
                        <string>10.15.0</string>
                    """
                    }

                    jvmArgs("--add-opens", "java.desktop/sun.lwawt=ALL-UNNAMED")
                    jvmArgs("--add-opens", "java.desktop/sun.lwawt.macosx=ALL-UNNAMED")
                    jvmArgs("-Dapple.awt.enableTemplateImages=true")
                    jvmArgs("-Dmac.bundleID=$bundleID")

                    val process = Runtime.getRuntime().exec("uname -m")
                    val result = process.inputStream.bufferedReader().use { it.readText() }.trim()

                    if (result == "x86_64" || buildFullPlatform) {
                        getJbrReleases(
                            "osx-x64",
                            jbrReleases,
                            jbrDir,
                        )
                    }

                    if (result == "arm64" || buildFullPlatform) {
                        getJbrReleases(
                            "osx-aarch64",
                            jbrReleases,
                            jbrDir,
                        )
                    }
                }
            }

            if (os.isWindows || buildFullPlatform) {
                windows {
                    targetFormats(TargetFormat.Msi)

                    val architecture = System.getProperty("os.arch")

                    if (architecture.contains("64")) {
                        getJbrReleases(
                            "windows-x64",
                            jbrReleases,
                            jbrDir,
                        )
                    } else {
                        throw IllegalArgumentException("Unsupported architecture: $architecture")
                    }
                }
            }

            if (os.isLinux || buildFullPlatform) {
                jvmArgs("-Dlinux.force.trayType=AppIndicator")
                linux {
                    targetFormats(TargetFormat.Deb)

                    getJbrReleases(
                        "linux-x64",
                        jbrReleases,
                        jbrDir,
                    )
                }
            }
        }
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
    systemProperty("appEnv", "TEST")
    testLogging {
        events = setOf(TestLogEvent.PASSED, TestLogEvent.SKIPPED, TestLogEvent.FAILED)
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        showExceptions = true
        showCauses = true
        showStackTraces = true
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
    arch: String,
    jbrReleases: JbrReleases,
    downDir: File,
) {
    val jbrDetails = jbrReleases.jbr[arch]!!
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
    arch: String,
    properties: Properties,
    downDir: Directory,
) {
    val chromeDriver = "chromedriver-$arch"
    val chromeHeadlessShell = "chrome-headless-shell-$arch"

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

fun extractFile(
    zip: ZipFile,
    entry: java.util.zip.ZipEntry,
    targetDir: Directory,
) {
    val targetFile = targetDir.file(entry.name.substringAfterLast("/"))
    targetFile.asFile.parentFile.mkdirs()
    zip.getInputStream(entry).use { input ->
        targetFile.asFile.outputStream().use { output ->
            input.copyTo(output)
        }
    }
    // Make the file executable
    targetFile.asFile.setExecutable(true, false)
    println("Extracted: ${targetFile.asFile.absolutePath}")
}

data class JbrReleases(
    var jbr: Map<String, JbrDetails> = mutableMapOf(),
)

data class JbrDetails(
    var url: String = "",
    var sha512: String = "",
)

fun extract(
    jar: File,
    outDir: File,
) {
    ZipFile(jar).use { zip ->
        zip.entries().asSequence().forEach { entry ->
            when (entry.name) {
                "org/openqa/selenium/manager/linux/selenium-manager" -> {
                    extractFile(zip, entry, outDir.resolve("linux-x64"))
                }
                "org/openqa/selenium/manager/macos/selenium-manager" -> {
                    extractFile(zip, entry, outDir.resolve("macos-x64"))
                    extractFile(zip, entry, outDir.resolve("macos-arm64"))
                }
                "org/openqa/selenium/manager/windows/selenium-manager.exe" -> {
                    extractFile(zip, entry, outDir.resolve("windows-x64"))
                }
            }
        }
    }
}

fun extractFile(
    zip: ZipFile,
    entry: java.util.zip.ZipEntry,
    targetDir: File,
) {
    val fileName = entry.name.substringAfterLast("/")
    val targetFileName =
        if (fileName.endsWith(".exe", ignoreCase = true)) {
            fileName.substringBeforeLast(".", "")
        } else {
            fileName
        }
    val targetFile = targetDir.resolve(targetFileName)
    targetFile.parentFile.mkdirs()
    if (!targetFile.exists() || targetFile.lastModified() < entry.lastModifiedTime.toMillis()) {
        zip.getInputStream(entry).use { input ->
            targetFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        targetFile.setExecutable(true, false)
        println("Extracted: ${targetFile.absolutePath}")
    } else {
        println("Skipped (up to date): ${targetFile.absolutePath}")
    }
}
