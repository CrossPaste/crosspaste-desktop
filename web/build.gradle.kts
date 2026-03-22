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
version = versionProperties.getProperty("version")

tasks.register("generateVersion") {
    val versionFile =
        project.projectDir
            .toPath()
            .parent
            .resolve("app/src/desktopMain/resources/crosspaste-version.properties")
            .toFile()
    val outputFile = project.file("src/shared/app/version.generated.ts")

    inputs.file(versionFile)
    outputs.file(outputFile)

    doLast {
        outputFile.parentFile.mkdirs()
        outputFile.writeText(
            """
            |// Auto-generated from app/src/desktopMain/resources/crosspaste-version.properties
            |// Do not edit manually — run ./gradlew :web:generateVersion to regenerate
            |
            |export const APP_VERSION = "${versionProperties.getProperty("version")}";
            |
            """.trimMargin(),
        )
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

val npmCmd = if (org.gradle.internal.os.OperatingSystem.current().isWindows) "npm.cmd" else "npm"

tasks.register<Exec>("npmInstall") {
    workingDir = projectDir
    commandLine(npmCmd, "install")
    inputs.file("package.json")
    outputs.dir("node_modules")
}

tasks.register<Exec>("npmBuild") {
    dependsOn("npmInstall", "generateVersion", "generateI18n", ":core:jsBrowserProductionLibraryDistribution")
    workingDir = projectDir
    commandLine(npmCmd, "run", "build")
    inputs.dir("src")
    inputs.dir("public")
    inputs.file("manifest.json")
    inputs.file("vite.config.ts")
    inputs.file("tsconfig.json")
    outputs.dir("dist")
}

tasks.register<Delete>("clean") {
    delete("dist", "node_modules")
}

tasks.register("build") {
    dependsOn("npmBuild")
}
