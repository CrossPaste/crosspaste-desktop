
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.compose.reload.gradle.AbstractComposeHotRun
import org.jetbrains.compose.reload.gradle.ComposeHotRun
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.Constructor
import java.io.FileReader
import java.security.MessageDigest
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
    // compose plugin 1.10.0 is not compatible with it, temporarily commented out
    // alias(libs.plugins.compose.hot.reload)
    alias(libs.plugins.conveyor)
    alias(libs.plugins.download)
    alias(libs.plugins.jetbrainsCompose)
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.ktlint)
}

buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        classpath(libs.yaml)
    }
}

composeCompiler {
    stabilityConfigurationFiles.add(rootProject.layout.projectDirectory.file("compose-stability.conf"))
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
                path.contains("\\commonMain\\kotlin\\androidx\\") ||
                path.contains("/commonMain/kotlin/androidx/") ||
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
            implementation(libs.awesome.brans)
            implementation(libs.coil.compose)
            implementation(libs.coil.svg)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.components.uiToolingPreview)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.runtime)
            implementation(libs.compose.ui)
            implementation(libs.cryptography.core)
            implementation(libs.filekit)
            implementation(libs.icons.material.symbols.rounded)
            implementation(libs.icons.material.symbols.rounded.filled)
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
            implementation(libs.ktor.client.websockets)
            implementation(libs.ktor.network)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.ktor.server.compression)
            implementation(libs.ktor.server.content.negotiation)
            implementation(libs.ktor.server.core)
            implementation(libs.ktor.server.status.pages)
            implementation(libs.ktor.server.websockets)
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
        }

        val desktopMain = getByName("desktopMain")

        desktopMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.caffeine)
            implementation(libs.compose.shimmer)
            implementation(libs.conveyor.control)
            implementation(libs.cryptography.provider.jdk)
            implementation(libs.hikaricp)
            implementation(libs.icu4j)
            implementation(libs.imageio.core)
            implementation(libs.imageio.jpeg)
            implementation(libs.jmdns)
            implementation(libs.jna)
            implementation(libs.jna.platform)
            implementation(libs.jnativehook)
            implementation(libs.ktor.server.cio)
            implementation(libs.ktor.server.netty)
            implementation(libs.logback.classic)
            implementation(libs.mcp.server)
            implementation(libs.metadata.extractor)
            implementation(libs.native.tray)
            implementation(libs.ph.css)
            implementation(libs.sqlite.driver)
            implementation(libs.tesseract.platform)
            implementation(libs.webp.imageio)
            implementation(libs.zxing.core)
        }

        commonTest.dependencies {
            implementation(libs.koin.test)
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.io.mockk)
            implementation(libs.ktor.client.mock)
            implementation(libs.ktor.server.test.host)
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
    macAmd64(libs.compose.desktop.macos.x64)
    macAarch64(libs.compose.desktop.macos.arm64)
    windowsAmd64(libs.compose.desktop.windows.x64)
    linuxAmd64(libs.compose.desktop.linux.x64)
    linuxAarch64(libs.compose.desktop.linux.arm64)
}

// Force Conveyor platform configurations to align transitive dependency versions with the main build.
// Without this, platform configs (macAmd64, windowsAmd64, etc.) resolve independently and may
// include older transitive versions (e.g., kotlinx-serialization-core 1.7.3 instead of 1.10.0),
// causing AbstractMethodError / NoSuchMethodError at runtime in MSIX packages.
afterEvaluate {
    val conveyorPlatformConfigs =
        listOf(
            "macAmd64",
            "macAarch64",
            "windowsAmd64",
            "windowsAarch64",
            "linuxAmd64",
            "linuxAmd64Muslc",
            "linuxAarch64",
            "linuxAarch64Muslc",
        )
    val serializationVersion =
        libs.versions.kotlinx.serialization
            .get()
    val kotlinVersion =
        libs.versions.kotlin
            .asProvider()
            .get()
    configurations.matching { it.name in conveyorPlatformConfigs }.configureEach {
        resolutionStrategy {
            force("org.jetbrains.kotlinx:kotlinx-serialization-core:$serializationVersion")
            force("org.jetbrains.kotlinx:kotlinx-serialization-core-jvm:$serializationVersion")
            force(
                libs.kotlinx.coroutines.core
                    .get()
                    .toString(),
            )
            force("org.jetbrains.kotlin:kotlin-stdlib:$kotlinVersion")
        }
        // Exclude AndroidX artifacts that duplicate JetBrains Compose Multiplatform ones.
        // The platform configs pull in androidx.* originals, while the main build uses
        // org.jetbrains.* re-published versions, causing duplicate classes on the classpath.
        exclude(group = "androidx.compose.runtime")
        exclude(group = "androidx.lifecycle")
        exclude(group = "androidx.savedstate")
    }
}

tasks.register<Copy>("copyDevProperties") {
    val targetPath = "src/desktopMain/resources/development.properties"
    val targetFile = layout.projectDirectory.file(targetPath).asFile
    from("src/desktopMain/resources/development.properties.template")
    into("src/desktopMain/resources")
    rename { "development.properties" }
    onlyIf("Target properties file does not exist") {
        !targetFile.exists()
    }
}

tasks.register("verifyChangelogVersion") {
    group = "verification"
    description =
        "Fails the build when the bundled product changelog (whats-new/*.md) " +
        "has no entry for the current app version, so releasing without release notes is impossible."

    val appVersion = versionProperties.getProperty("version")
    val changelogDir = layout.projectDirectory.dir("src/desktopMain/resources/whats-new")
    // At least Simplified Chinese and English must be kept in sync with the version.
    val requiredChangelogs = listOf("zh.md", "en.md")
    val headerRegex = Regex("""^#\s*\[([^]]+)]\s*-\s*.+$""")

    inputs.property("appVersion", appVersion)
    requiredChangelogs.forEach { inputs.file(changelogDir.file(it)) }

    doLast {
        val problems = mutableListOf<String>()
        requiredChangelogs.forEach { name ->
            val file = changelogDir.file(name).asFile
            if (!file.exists()) {
                problems.add("missing changelog file: ${file.path}")
                return@forEach
            }
            val latest =
                file
                    .readLines()
                    .firstNotNullOfOrNull { line ->
                        headerRegex
                            .find(line.trim())
                            ?.groupValues
                            ?.get(1)
                            ?.trim()
                    }
            when {
                latest == null ->
                    problems.add("$name has no version header in the form '# [version] - date'")
                latest != appVersion ->
                    problems.add("$name latest entry is [$latest] but app version is [$appVersion]")
            }
        }
        if (problems.isNotEmpty()) {
            throw GradleException(
                buildString {
                    appendLine("Changelog version check failed for app version [$appVersion].")
                    appendLine("Add a '# [$appVersion] - <date>' section to each file under")
                    appendLine("src/desktopMain/resources/whats-new/ before building:")
                    problems.forEach { appendLine("  - $it") }
                },
            )
        }
    }
}

tasks.named("desktopProcessResources") {
    dependsOn("copyDevProperties", "verifyChangelogVersion")
}

private fun initJvmArgs(
    jvmArgs: (Array<String>) -> Unit,
    buildFullPlatform: Boolean = false,
) {
    // Add system properties that need to be set for all platforms
    val appEnv = project.findProperty("appEnv")?.toString() ?: "DEVELOPMENT"
    val globalListener = project.findProperty("globalListener")?.toString() ?: "true"
    jvmArgs(
        arrayOf(
            "-DappEnv=$appEnv",
            "-Djava.net.preferIPv4Stack=true",
            "-Djava.net.preferIPv6Addresses=false",
            "-DglobalListener=$globalListener",
            "-Dio.netty.maxDirectMemory=268435456",
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

                    val architecture = System.getProperty("os.arch")

                    if (architecture == "amd64" || architecture == "x86_64" || buildFullPlatform) {
                        getJbrReleases(
                            "linux-x64",
                            jbrReleases,
                            jbrDir,
                        )
                    }

                    if (architecture.contains("aarch64") || architecture.contains("arm") || buildFullPlatform) {
                        getJbrReleases(
                            "linux-aarch64",
                            jbrReleases,
                            jbrDir,
                        )
                    }
                }
            }
        }
    }
}

tasks.withType<Test> {
    useJUnitPlatform {
        // Fast tier by default: suites tagged @IntegrationTest (real processes,
        // full handshakes, wall-clock timing) only run with -PintegrationTests,
        // which release/beta CI builds pass. See com.crosspaste.test.IntegrationTest.
        if (!providers.gradleProperty("integrationTests").isPresent) {
            excludeTags("integration")
        }
    }
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
    // Check if the configuration is either Resolvable or Consumable
    // In Gradle 9, only these types allow attribute modification; the legacy
    // "archives" configuration reports consumable but deprecates attribute calls
    if (name != "archives" && (isCanBeResolved || isCanBeConsumed)) {
        attributes {
            // https://github.com/JetBrains/compose-jb/issues/1404#issuecomment-1146894731
            attribute(Attribute.of("ui", String::class.java), "awt")
        }
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

// Downloads the pinned libcrypto binaries (openssl.yaml) and stages each at
// openssl/<target>/<runtime lib name>. conveyor.conf adds those files as bare
// per-machine inputs; Conveyor moves shared libraries from inputs into the JVM
// lib directory (= skiko.library.path) and signs them — the same directory the
// skiko/tesseract natives land in. Packaging-only: dev/test resolve libcrypto
// from the environment and never run this task. The checksum chain ends at the
// downloaded file; the staging step is a local lossless copy.
tasks.register("prepareOpenSslLibs") {
    group = "build"
    description = "Download pinned libcrypto binaries and stage them per platform for Conveyor bundling."

    val openSslYamlFile = project.projectDir.resolve("openssl.yaml")
    val openSslDir = project.projectDir.resolve("openssl")

    // Optional target subset (-PopenSslTargets=linux-x64,linux-arm64) so
    // machine-scoped CI builds (e.g. the linux-only beta) don't couple their
    // success to the reachability of the other platforms' binaries.
    val requestedTargets = providers.gradleProperty("openSslTargets")

    inputs.file(openSslYamlFile)
    inputs.property("openSslTargets", requestedTargets.orElse(""))
    outputs.dir(openSslDir)

    doLast {
        val allTargets = loadOpenSslReleases(openSslYamlFile).openssl.targets
        val selectedTargets =
            requestedTargets.orNull?.split(",")?.map(String::trim)?.let { names ->
                names.forEach { name ->
                    if (name !in allTargets) {
                        throw GradleException("Unknown OpenSSL target '$name'; openssl.yaml defines ${allTargets.keys}")
                    }
                }
                allTargets.filterKeys(names::contains)
            } ?: allTargets
        openSslDir.mkdirs()
        // Drop leftovers from earlier versions (stale entries keyed by the full
        // target set, so a filtered run never deletes other targets' files).
        val expectedDownloads = allTargets.map { (_, details) -> details.url.substringAfterLast("/") }.toSet()
        openSslDir.listFiles()?.forEach { entry ->
            if (entry.isFile && entry.name !in expectedDownloads) {
                entry.delete()
            }
            if (entry.isDirectory && entry.name !in allTargets) {
                entry.deleteRecursively()
            }
        }
        selectedTargets.forEach { (target, details) ->
            stageOpenSslTarget(openSslDir, target, details)
        }
    }
}

// Development-run parity with packaged builds: development runs (`app:run`,
// `desktopRun`, every hot-reload entry point) stage the current platform's
// pinned libcrypto first — JBR-style automatic download — and point the run
// JVM at it through the crosspaste.libcrypto.path override, so pairing v3
// works in development without a system OpenSSL. Only DEVELOPMENT runs are
// wired: packaged builds and tests are unaffected, and `-PappEnv=PRODUCTION`
// or BETA runs resolve the bundled library, which ignores overrides. An
// explicit override — `-Dcrosspaste.libcrypto.path=...` on the Gradle
// invocation, or CROSSPASTE_LIBCRYPTO_PATH in the environment — wins over the
// staged library and skips the download entirely, so an offline machine with
// its own libcrypto still starts.
val devRunAppEnv: String = project.findProperty("appEnv")?.toString() ?: "DEVELOPMENT"
val devLibcryptoPropertyOverride: String? = System.getProperty("crosspaste.libcrypto.path")
val devLibcryptoOverridden: Boolean =
    devLibcryptoPropertyOverride != null || System.getenv("CROSSPASTE_LIBCRYPTO_PATH") != null

// Lazy so openssl.yaml is only parsed when a development run task is actually
// configured, never for tests or unrelated builds; null on platforms
// openssl.yaml defines no binary for.
val devOpenSslLibFile: File? by lazy {
    currentOpenSslTarget()?.let { target ->
        project.projectDir
            .resolve("openssl")
            .resolve(target)
            .resolve(
                loadOpenSslReleases(project.projectDir.resolve("openssl.yaml"))
                    .openssl.targets
                    .getValue(target)
                    .libName,
            )
    }
}

val prepareDevOpenSslLib =
    tasks.register("prepareDevOpenSslLib") {
        group = "build"
        description =
            "Download and stage the pinned libcrypto for the current platform so development runs can load it."

        val openSslYamlFile = project.projectDir.resolve("openssl.yaml")
        val openSslDir = project.projectDir.resolve("openssl")
        val target = currentOpenSslTarget()

        inputs.file(openSslYamlFile)
        inputs.property("openSslTarget", target ?: "")
        if (target != null) {
            outputs.dir(openSslDir.resolve(target))
        }

        doLast {
            if (target == null) {
                throw GradleException(
                    "openssl.yaml defines no libcrypto binary for this platform " +
                        "(os.name=${System.getProperty("os.name")}, os.arch=${System.getProperty("os.arch")})",
                )
            }
            val details =
                loadOpenSslReleases(openSslYamlFile).openssl.targets[target]
                    ?: throw GradleException("openssl.yaml does not define target '$target'")
            stageOpenSslTarget(openSslDir, target, details)
        }
    }

// Wires one development run task: stage-before-launch plus the override
// property — or, when the developer supplied an explicit override, just
// forward it to the launched JVM without downloading anything.
fun JavaExec.wireDevLibcrypto() {
    if (devRunAppEnv != "DEVELOPMENT") {
        return
    }
    if (devLibcryptoOverridden) {
        // An environment variable reaches the child process on its own; a
        // Gradle -D property does not, so forward it (app-side priority stays
        // property > environment > platform candidates).
        devLibcryptoPropertyOverride?.let { systemProperty("crosspaste.libcrypto.path", it) }
        return
    }
    val libFile = devOpenSslLibFile
    if (libFile == null) {
        logger.warn(
            "openssl.yaml defines no libcrypto binary for this platform; pairing v3 needs " +
                "a system OpenSSL 3 or a crosspaste.libcrypto.path override to load.",
        )
        return
    }
    dependsOn(prepareDevOpenSslLib)
    systemProperty("crosspaste.libcrypto.path", libFile.absolutePath)
}

tasks.withType<JavaExec>().configureEach {
    if (name == "run" || name == "desktopRun") {
        wireDevLibcrypto()
    }
}

// Hot-reload entry points: the sync variants are JavaExec subclasses and feed
// the argfiles the async variants launch from, so wiring them carries the
// override property into both; the async tasks additionally need the staging
// dependency themselves because they do not depend on their underlying run
// task.
tasks.withType<AbstractComposeHotRun>().configureEach {
    wireDevLibcrypto()
}

// ComposeHotAsyncRun is internal to the hot-reload plugin, so the async
// variants are matched by name instead of type.
tasks.matching { it.name.startsWith("hot") && it.name.endsWith("Async") }.configureEach {
    if (devRunAppEnv == "DEVELOPMENT" && !devLibcryptoOverridden && devOpenSslLibFile != null) {
        dependsOn(prepareDevOpenSslLib)
    }
}

// Downloads (with SHA-256 self-healing) and stages one openssl.yaml target at
// openssl/<target>/<libName>. Shared by the packaging task (all targets) and
// the development-run task (current platform only).
fun stageOpenSslTarget(
    openSslDir: File,
    target: String,
    details: OpenSslTarget,
) {
    openSslDir.mkdirs()
    val libFile = openSslDir.resolve(details.url.substringAfterLast("/"))
    if (!libFile.exists() || sha256Hex(libFile) != details.sha256) {
        download.run {
            src { details.url }
            dest { openSslDir }
            overwrite(true)
            tempAndMove(true)
        }
    }
    val actualSha256 = sha256Hex(libFile)
    if (actualSha256 != details.sha256) {
        throw GradleException(
            "SHA-256 mismatch for ${libFile.name}: expected ${details.sha256}, got $actualSha256",
        )
    }
    val targetDir = openSslDir.resolve(target)
    targetDir.mkdirs()
    targetDir.listFiles()?.forEach { staged ->
        if (staged.name != details.libName) {
            staged.delete()
        }
    }
    libFile.copyTo(targetDir.resolve(details.libName), overwrite = true)
}

// The openssl.yaml target key for the machine running the build. Explicit
// os/arch mapping: combinations openssl.yaml has no binary for (e.g. Windows
// ARM64) return null instead of silently borrowing another target's library.
fun currentOpenSslTarget(): String? {
    val os = DefaultNativePlatform.getCurrentOperatingSystem()
    val arch =
        when (System.getProperty("os.arch").lowercase()) {
            "aarch64", "arm64" -> "arm64"
            "amd64", "x86_64", "x64" -> "x64"
            else -> return null
        }
    return when {
        os.isMacOsX -> "macos-$arch"
        os.isLinux -> "linux-$arch"
        os.isWindows && arch == "x64" -> "windows-x64"
        else -> null
    }
}

fun sha256Hex(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

fun loadOpenSslReleases(file: File): OpenSslReleases {
    val yaml = Yaml(Constructor(OpenSslReleases::class.java, LoaderOptions()))
    file.inputStream().use {
        return yaml.load(it)
    }
}

data class OpenSslReleases(
    var openssl: OpenSslConfig = OpenSslConfig(),
)

data class OpenSslConfig(
    var version: String = "",
    var targets: Map<String, OpenSslTarget> = mutableMapOf(),
)

data class OpenSslTarget(
    var url: String = "",
    var sha256: String = "",
    var libName: String = "",
)
