package com.crosspaste.paste

import com.crosspaste.app.AppFileType
import com.crosspaste.path.UserDataPathProvider
import kotlinx.datetime.Clock
import okio.Path

class DesktopPasteExporter(
    private val userDataPathProvider: UserDataPathProvider,
) : PasteExporter {
    override fun export(path: Path) {
        val backupJsonIndex =
            userDataPathProvider.resolve(
                fileName = "backup-index.json",
                appFileType = AppFileType.TEMP,
            )

        val currentTime = Clock.System.now().toEpochMilliseconds()
        val exportFilePath =
            userDataPathProvider.resolve(
                basePath = path,
                path = "crosspaste-backup-$currentTime.cpb",
                isFile = true,
            )
    }
}
