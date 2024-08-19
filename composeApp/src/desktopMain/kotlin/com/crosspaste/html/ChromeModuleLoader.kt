package com.crosspaste.html

import com.crosspaste.module.AbstractModuleLoader
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.utils.noOptionParent
import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import okio.Path
import java.io.FileInputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.zip.ZipInputStream

class ChromeModuleLoader(
    override val userDataPathProvider: UserDataPathProvider,
) : AbstractModuleLoader() {

    override val logger: KLogger = KotlinLogging.logger {}

    override fun installModule(
        downloadPath: Path,
        installPath: Path,
    ): Boolean {
        if (!downloadPath.toString().lowercase().endsWith(".zip")) {
            println("Error: Downloaded file is not a zip archive")
            return false
        }

        try {
            val installDir = installPath.noOptionParent
            fileUtils.deleteFile(installDir)
            fileUtils.createDir(installDir)

            ZipInputStream(FileInputStream(downloadPath.toFile())).use { zipIn ->
                var entry = zipIn.nextEntry
                while (entry != null) {
                    val filePath = installDir.resolve(entry.name)

                    if (!entry.isDirectory) {
                        fileUtils.createDir(filePath.noOptionParent)
                        Files.copy(zipIn, filePath.toNioPath(), StandardCopyOption.REPLACE_EXISTING)
                    }
                    zipIn.closeEntry()
                    entry = zipIn.nextEntry
                }
            }
            logger.info { "Module installed successfully" }
            return true
        } catch (e: Exception) {
            logger.error { "Error during module installation: ${e.message}" }
            return false
        }
    }
}
