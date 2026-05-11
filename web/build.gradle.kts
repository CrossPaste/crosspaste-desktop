import java.io.FileReader
import java.io.InputStreamReader
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
version =
    checkNotNull(versionProperties.getProperty("version")) {
        "Missing 'version' in crosspaste-version.properties"
    }

fun gitRevision(): String? =
    runCatching {
        val process =
            ProcessBuilder("git", "rev-list", "--count", "HEAD")
                .directory(project.projectDir.parentFile)
                .redirectErrorStream(true)
                .start()
        process.inputStream.bufferedReader().use { it.readText() }.trim().also {
            process.waitFor()
        }
    }.getOrNull()?.takeIf { it.isNotBlank() && it.all(Char::isDigit) }

tasks.register("generateVersion") {
    val versionFile =
        project.projectDir
            .toPath()
            .parent
            .resolve("app/src/desktopMain/resources/crosspaste-version.properties")
            .toFile()
    val tsOutputFile = project.file("src/shared/app/version.generated.ts")
    val manifestFile = project.file("manifest.json")

    inputs.file(versionFile)
    outputs.file(tsOutputFile)
    outputs.file(manifestFile)

    doLast {
        val props = Properties()
        FileReader(versionFile).use { props.load(it) }
        val revision = props.getProperty("revision")
        val fullVersion = if (revision.isNullOrBlank()) version else "$version.$revision"

        tsOutputFile.parentFile.mkdirs()
        tsOutputFile.writeText(
            """
            |// Auto-generated from app/src/desktopMain/resources/crosspaste-version.properties
            |// Do not edit manually — run ./gradlew :web:generateVersion to regenerate
            |
            |export const APP_VERSION = "$fullVersion";
            |
            """.trimMargin(),
        )

        val manifestText = manifestFile.readText()
        val updatedManifest =
            manifestText.replaceFirst(
                Regex("\"version\"\\s*:\\s*\"[^\"]*\""),
                "\"version\": \"$fullVersion\"",
            )
        check(updatedManifest != manifestText || manifestText.contains("\"version\": \"$fullVersion\"")) {
            "Failed to update version field in $manifestFile"
        }
        if (updatedManifest != manifestText) {
            manifestFile.writeText(updatedManifest)
        }
    }
}

tasks.register("generateI18n") {
    val i18nDir =
        project.projectDir
            .toPath()
            .parent
            .resolve("app/src/desktopMain/resources/i18n")
            .toFile()
    val outputFile = project.file("src/shared/i18n/translations.generated.ts")

    inputs.dir(i18nDir)
    outputs.file(outputFile)

    doLast {
        val allTranslations = sortedMapOf<String, Map<String, String>>()

        i18nDir
            .listFiles { _, name -> name.endsWith(".properties") }
            ?.sorted()
            ?.forEach { file ->
                val lang = file.nameWithoutExtension
                val props = Properties()
                InputStreamReader(file.inputStream(), Charsets.UTF_8).use { props.load(it) }
                allTranslations[lang] =
                    props.entries
                        .associate { it.key.toString() to it.value.toString() }
                        .toSortedMap()
            }

        val sb = StringBuilder()
        sb.appendLine("// Auto-generated from app/src/desktopMain/resources/i18n/*.properties")
        sb.appendLine("// Do not edit manually — run ./gradlew :web:generateI18n to regenerate")
        sb.appendLine()
        sb.appendLine("export const translations: Record<string, Record<string, string>> = {")

        for ((lang, entries) in allTranslations) {
            sb.appendLine("  \"$lang\": {")
            for ((key, value) in entries) {
                val escaped =
                    value
                        .replace("\\", "\\\\")
                        .replace("\"", "\\\"")
                        .replace("\n", "\\n")
                sb.appendLine("    \"$key\": \"$escaped\",")
            }
            sb.appendLine("  },")
        }

        sb.appendLine("};")

        outputFile.parentFile.mkdirs()
        outputFile.writeText(sb.toString())
    }
}

fun resolveNpmCommand(): String {
    val isWindows = org.gradle.internal.os.OperatingSystem.current().isWindows
    val fallback = if (isWindows) "npm.cmd" else "npm"
    if (isWindows) return fallback

    val candidatePaths =
        buildList {
            add("/opt/homebrew/bin/npm")
            add("/usr/local/bin/npm")
            add("/usr/bin/npm")
            val home = System.getProperty("user.home")
            File("$home/.nvm/versions/node")
                .takeIf { it.isDirectory }
                ?.listFiles { f -> f.isDirectory }
                ?.sortedByDescending { it.name }
                ?.forEach { add("${it.absolutePath}/bin/npm") }
        }
    candidatePaths.firstOrNull { File(it).canExecute() }?.let { return it }

    val userShell = System.getenv("SHELL")?.takeIf { File(it).canExecute() } ?: "/bin/bash"
    return sequenceOf(
        listOf(userShell, "-lc", "command -v npm"),
        listOf("/bin/bash", "-lc", "command -v npm"),
    ).firstNotNullOfOrNull { cmd ->
        runCatching {
            val process =
                ProcessBuilder(cmd)
                    .redirectErrorStream(false)
                    .start()
            val output = process.inputStream.bufferedReader().use { it.readText() }
            process.waitFor()
            output.lineSequence()
                .map { it.trim() }
                .firstOrNull { it.isNotBlank() && File(it).canExecute() }
        }.getOrNull()
    } ?: fallback
}

val npmCmd: String by lazy { resolveNpmCommand() }

val npmBinDir: String? by lazy {
    File(npmCmd)
        .takeIf { it.isAbsolute && it.exists() }
        ?.parentFile
        ?.absolutePath
}

fun ExecSpec.injectNpmBinDir() {
    val dir = npmBinDir ?: return
    val currentPath = System.getenv("PATH").orEmpty()
    if (currentPath.split(File.pathSeparator).none { it == dir }) {
        environment("PATH", "$dir${File.pathSeparator}$currentPath")
    }
}

tasks.register<Exec>("npmInstall") {
    workingDir = projectDir
    inputs.file("package.json")
    outputs.dir("node_modules")
    doFirst {
        commandLine(npmCmd, "install")
        injectNpmBinDir()
    }
}

tasks.register<Exec>("npmBuild") {
    dependsOn("npmInstall", "generateVersion", "generateI18n", ":core:jsBrowserProductionLibraryDistribution")
    workingDir = projectDir
    inputs.dir("src")
    inputs.dir("public")
    inputs.file("manifest.json")
    inputs.file("vite.config.ts")
    inputs.file("tsconfig.json")
    outputs.dir("dist")
    doFirst {
        commandLine(npmCmd, "run", "build")
        injectNpmBinDir()
    }
}

tasks.register("patchDistManifestRevision") {
    dependsOn("npmBuild")
    val distManifest = project.file("dist/manifest.json")
    outputs.file(distManifest)
    outputs.upToDateWhen { false }

    doLast {
        val revision =
            versionProperties.getProperty("revision")?.takeIf { it.isNotBlank() }
                ?: gitRevision()
                ?: error("Cannot resolve revision: 'revision' missing in properties and 'git rev-list --count HEAD' failed")
        val fullVersion = "$version.$revision"

        check(distManifest.exists()) { "dist/manifest.json not found — run :web:npmBuild first" }
        val text = distManifest.readText()
        val patched =
            text.replaceFirst(
                Regex("\"version\"\\s*:\\s*\"[^\"]*\""),
                "\"version\": \"$fullVersion\"",
            )
        check(patched.contains("\"version\": \"$fullVersion\"")) {
            "Failed to inject version into $distManifest"
        }
        distManifest.writeText(patched)
        logger.lifecycle("Patched dist/manifest.json version → $fullVersion")
    }
}

tasks.register<Zip>("packageExtension") {
    dependsOn("patchDistManifestRevision")
    from(layout.projectDirectory.dir("dist"))
    archiveFileName.set(
        provider {
            val rev =
                versionProperties.getProperty("revision")?.takeIf { it.isNotBlank() }
                    ?: gitRevision()
                    ?: error("Cannot resolve revision for zip name")
            "crosspaste-extension-$version.$rev.zip"
        },
    )
    destinationDirectory.set(layout.buildDirectory.dir("distributions"))
    exclude("**/.DS_Store")

    doLast {
        logger.lifecycle("Chrome extension zip: ${archiveFile.get().asFile.absolutePath}")
    }
}

tasks.register<Delete>("clean") {
    delete("dist", "node_modules")
}

tasks.register("build") {
    dependsOn("npmBuild")
}
