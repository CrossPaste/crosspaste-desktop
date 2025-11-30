
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.compose.reload.gradle.ComposeHotRun
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.Constructor
import java.io.FileReader
import java.util.Properties

val versionProperties = Properties()
versionProperties.load(
    FileReader(
        project.projectDir
            .toPath()
            .resolve("src/desktopMain/resources/crosspaste-version.properties")
            .toFile(),
    ),
)
group = "com.crosspaste"
version = versionProperties.getProperty("version")

plugins {
    alias(libs.plugins.atomicfu)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose.hot.reload)
    alias(libs.plugins.conveyor)
    alias(libs.plugins.download)
    alias(libs.plugins.jetbrainsCompose)
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.sqlDelight)
}

buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        classpath(libs.yaml)
    }
}

sqldelight {
    databases {
        create("Database") {
            packageName = "com.crosspaste"
            dialect("app.cash.sqldelight:sqlite-3-25-dialect:2.0.2")
        }
    }
}

ktlint {
    verbose = true
    android = false
    ignoreFailures = false
    filter {
        exclude { element ->
            val path = element.path
            path.contains("\\generated\\") ||
                path.contains("/generated/") ||
                path.contains("\\desktopTest\\") ||
                path.contains("/desktopTest/") ||
                path.contains("\\commonMain\\kotlin\\androidx\\") ||
                path.contains("/commonMain/kotlin/androidx/") ||
                path.contains("\\db\\") ||
                path.contains("/db/") ||
                path.endsWith("Database.kt") ||
                path.endsWith("DatabaseImpl.kt")
        }
    }
}

kotlin {
    jvm("desktop") {}

    sourceSets {
        commonMain.dependencies {
            implementation(project(":shared"))
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
            implementation(libs.coil.compose)
            implementation(libs.coil.svg)
            implementation(libs.cryptography.core)
            implementation(libs.filekit)
            implementation(libs.koin.compose)
            implementation(libs.koin.core)
            implementation(libs.koin.viewmodel)
            implementation(libs.kotlin.logging)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.coroutines.swing)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.ksoup)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.cio)
            implementation(libs.ktor.client.logging)
            implementation(libs.ktor.network)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.ktor.server.compression)
            implementation(libs.ktor.server.content.negotiation)
            implementation(libs.ktor.server.core)
            implementation(libs.ktor.server.status.pages)
            implementation(libs.lifecycle.common)
            implementation(libs.lifecycle.runtime)
            implementation(libs.lifecycle.runtime.compose)
            implementation(libs.lifecycle.viewmodel)
            implementation(libs.lifecycle.viewmodel.compose)
            implementation(libs.material.desktop)
            implementation(libs.navigation.compose)
            implementation(libs.okio)
            implementation(libs.richeditor)
            implementation(libs.semver)
            implementation(libs.sqldelight.coroutines.extensions)
        }

        val desktopMain by getting

        desktopMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.caffeine)
            implementation(libs.compose.shimmer)
            implementation(libs.conveyor.control)
            implementation(libs.cryptography.provider.jdk)
            implementation(libs.icu4j)
            implementation(libs.imageio.core)
            implementation(libs.imageio.jpeg)
            implementation(libs.jewel.decorated.window)
            implementation(libs.jewel.foundation)
            implementation(libs.jewel.int.ui.decorated.window)
            implementation(libs.jewel.int.ui.standalone)
            implementation(libs.jewel.ui)
            implementation(libs.jmdns)
            implementation(libs.jna)
            implementation(libs.jna.platform)
            implementation(libs.jnativehook)
            implementation(libs.ktor.server.netty)
            implementation(libs.logback.classic)
            implementation(libs.ph.css)
            implementation(libs.sqlite.driver)
            implementation(libs.system.tray)
            implementation(libs.tesseract.platform)
            implementation("com.github.Dansoftowner:jSystemThemeDetector:3.9.1") {
                exclude(group = "net.java.dev.jna")
            }
            implementation(libs.webp.imageio)
            implementation(libs.zxing.core)
        }

        commonTest.dependencies {
            implementation(libs.koin.test)
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.io.mockk)
            implementation(libs.turbine)
        }

        configurations.configureEach {
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

private fun initJvmArgs(
    jvmArgs: (Array<String>) -> Unit,
    buildFullPlatform: Boolean = false,
) {
    // Add system properties that need to be set for all platforms
    val loggerLevel = project.findProperty("loggerLevel")?.toString() ?: "info"
    val appEnv = project.findProperty("appEnv")?.toString() ?: "DEVELOPMENT"
    val globalListener = project.findProperty("globalListener")?.toString() ?: "true"
    jvmArgs(
        arrayOf(
            "-DloggerLevel=$loggerLevel",
            "-DappEnv=$appEnv",
            "-Djava.net.preferIPv4Stack=true",
            "-Djava.net.preferIPv6Addresses=false",
            "-DglobalListener=$globalListener",
            "-Dio.netty.maxDirectMemory=268435456",
            "-DloggerDebugPackages=com.crosspaste.routing,com.crosspaste.net.clientapi,com.crosspaste.net.plugin",
        ),
    )

    // Open modules required for all platforms
    jvmArgs(arrayOf("--add-opens", "java.desktop/sun.awt=ALL-UNNAMED"))
    jvmArgs(arrayOf("--add-opens", "java.desktop/java.awt.peer=ALL-UNNAMED"))

    val os: OperatingSystem = DefaultNativePlatform.getCurrentOperatingSystem()

    if (os.isMacOsX || buildFullPlatform) {
        jvmArgs(arrayOf("--add-opens", "java.desktop/sun.lwawt=ALL-UNNAMED"))
        jvmArgs(arrayOf("--add-opens", "java.desktop/sun.lwawt.macosx=ALL-UNNAMED"))
        jvmArgs(
            arrayOf(
                "-Dapple.awt.enableTemplateImages=true",
                "-Dmac.bundleID=com.crosspaste.mac",
            ),
        )
    }

    if (os.isLinux || buildFullPlatform) {
        jvmArgs(arrayOf("-Dlinux.force.trayType=AppIndicator"))
    }
}

tasks.withType<ComposeHotRun>().configureEach {
    initJvmArgs(this::jvmArgs)
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
                val compileSwiftTask =
                    tasks.register<Exec>("compileSwift") {
                        group = "build"
                        description = "Compile Swift code and output the dylib to generated directory."

                        onlyIf {
                            DefaultNativePlatform.getCurrentOperatingSystem().isMacOsX
                        }

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

                        val inputFile = layout.projectDirectory.file("src/desktopMain/swift/MacosApi.swift").asFile

                        val outputDir =
                            layout.buildDirectory
                                .dir("generated/swift/$archDir")
                                .get()
                                .asFile
                        val outputFile = File(outputDir, "libMacosApi.dylib")

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

                        doFirst {
                            outputDir.mkdirs()
                        }
                    }

                tasks.named<ProcessResources>("desktopProcessResources") {
                    dependsOn(compileSwiftTask)

                    from(
                        compileSwiftTask.map {
                            layout.buildDirectory.dir("generated/swift").get()
                        },
                    ) {
                        include("**/*.dylib")
                        into("")
                    }
                }

                tasks.named("desktopJar") {
                    dependsOn("desktopProcessResources")
                }

                tasks.named("desktopTest") {
                    dependsOn("desktopProcessResources")
                }

                tasks.findByName("ktlintCommonMainSourceSetCheck")?.let {
                    compileSwiftTask.configure {
                        dependsOn(it)
                    }
                }
            } else {
                // If it is to build the full platform
                // then the GitHub action will prepare the dylibs files compiled under
                // the Intel and ARM architectures to the dylib directory
                val copyDylibsTask =
                    tasks.register<Copy>("copyDylibs") {
                        from("dylib/")
                        into(layout.buildDirectory.dir("generated/swift"))

                        doFirst {
                            destinationDir.mkdirs()
                        }
                    }

                tasks.named<ProcessResources>("desktopProcessResources") {
                    dependsOn(copyDylibsTask)

                    from(
                        copyDylibsTask.map {
                            layout.buildDirectory.dir("generated/swift").get()
                        },
                    ) {
                        include("**/*.dylib")
                        into("")
                    }
                }

                tasks.named("desktopJar") {
                    dependsOn("desktopProcessResources")
                }

                tasks.named("desktopTest") {
                    dependsOn("desktopProcessResources")
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

            val appEnv = project.findProperty("appEnv")?.toString() ?: "DEVELOPMENT"

            val jvmArgsLambda: (Array<String>) -> Unit = { args ->
                args.forEach {
                    jvmArgs(it)
                }
            }

            initJvmArgs(jvmArgsLambda, buildFullPlatform)

            if (appEnv != "DEVELOPMENT") {
                tasks.withType<Jar> {
                    exclude("development.properties**")
                }
            }

            // Add download info of jbr on all platforms
            val jbrYamlFile =
                project.projectDir
                    .toPath()
                    .resolve("jbr.yaml")
                    .toFile()
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

                    val process = Runtime.getRuntime().exec(arrayOf("uname", "-m"))
                    val result =
                        process.inputStream
                            .bufferedReader()
                            .use { it.readText() }
                            .trim()

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
                linux {
                    targetFormats(TargetFormat.Deb)

                    modules("jdk.security.auth")

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
    systemProperty("project.root", rootProject.rootDir.absolutePath)
    testLogging {
        events = setOf(TestLogEvent.PASSED, TestLogEvent.SKIPPED, TestLogEvent.FAILED)
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        showExceptions = true
        showCauses = true
        showStackTraces = true
    }
    jvmArgs(
        "--add-opens",
        "java.base/java.net=ALL-UNNAMED",
        "--add-opens",
        "java.base/java.lang.reflect=ALL-UNNAMED",
    )
}

afterEvaluate {
    tasks.withType<ComposeHotRun>().configureEach {
        val os: OperatingSystem = DefaultNativePlatform.getCurrentOperatingSystem()
        if (os.isMacOsX) {
            tasks.findByName("compileSwift")?.let {
                dependsOn(it)
            }
        }
    }

    tasks.findByName("desktopRun")?.apply {
        dependsOn("desktopProcessResources")
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
    downJbrReleases(jbrDetails.url, jbrDetails.sha512, fileName, downDir)
}

fun downJbrReleases(
    url: String,
    sha512: String,
    fileName: String,
    downDir: File,
) {
    val file = downDir.resolve(fileName)

    if (!file.exists()) {
        download.run {
            src { url }
            dest { downDir }
            overwrite(true)
            tempAndMove(true)
        }
        verifyChecksum.run {
            src { file }
            algorithm("SHA-512")
            checksum(sha512)
        }
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
