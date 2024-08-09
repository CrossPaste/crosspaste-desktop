package com.crosspaste.path

import com.crosspaste.paste.item.PasteFiles
import com.crosspaste.presist.FilesIndexBuilder
import okio.Path

interface UserDataPathProvider : PathProvider {

    fun getUserDataPath(): Path

    fun resolve(
        appInstanceId: String,
        dateString: String,
        pasteId: Long,
        pasteFiles: PasteFiles,
        isPull: Boolean,
        filesIndexBuilder: FilesIndexBuilder? = null,
    )

    fun migration(
        migrationPath: Path,
        realmMigrationAction: (Path) -> Unit,
    )
}
