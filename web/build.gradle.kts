import java.io.FileReader
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

tasks.register<Exec>("npmInstall") {
    workingDir = projectDir
    commandLine("npm", "install")
    inputs.file("package.json")
    outputs.dir("node_modules")
}

tasks.register<Exec>("npmBuild") {
    dependsOn("npmInstall", ":core:jsBrowserProductionLibraryDistribution")
    workingDir = projectDir
    commandLine("npm", "run", "build")
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
