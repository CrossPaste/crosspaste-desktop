package com.crosspaste.html

import com.crosspaste.module.AbstractModuleLoader
import com.crosspaste.path.UserDataPathProvider
import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import okio.Path
import java.util.zip.ZipInputStream
import kotlin.io.path.createDirectories

class ChromeModuleLoader(
    override val userDataPathProvider: UserDataPathProvider,
) : AbstractModuleLoader() {

    override val logger: KLogger = KotlinLogging.logger {}

    override fun installModule(
        downloadPath: Path,
        installPath: Path,
    ): Boolean {
        if (!downloadPath.toString().lowercase().endsWith(".zip")) {
            logger.error { "Error: Downloaded file is not a zip archive" }
            return false
        }

        try {
            // Decompress the downloaded file to installPath, this function needs to be idempotent and can be executed repeatedly
            unzipFile(downloadPath, installPath)
            logger.info { "Module installed successfully" }
            return true
        } catch (e: Exception) {
            logger.error { "Error during module installation: ${e.message}" }
            return false
        }
    }

    private fun unzipFile(
        zipFile: Path,
        destDir: Path,
    ) {
        // Ensure the destination directory exists
        destDir.toNioPath().createDirectories()

        ZipInputStream(zipFile.toFile().inputStream()).use { zis ->
            var zipEntry = zis.nextEntry
            while (zipEntry != null) {
                val newFile = destDir.resolve(zipEntry.name)
                if (zipEntry.isDirectory) {
                    newFile.toNioPath().createDirectories()
                } else {
                    // Create parent directories if they don't exist
                    newFile.parent?.toNioPath()?.createDirectories()
                    // Write file content
                    newFile.toFile().outputStream().use { fos ->
                        zis.copyTo(fos)
                    }
                }
                zipEntry = zis.nextEntry
            }
            zis.closeEntry()
        }
    }
}
