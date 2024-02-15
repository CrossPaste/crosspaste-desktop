package com.clipevery.clip.service

import com.clipevery.app.AppFileType
import com.clipevery.clip.ClipItemService
import com.clipevery.path.DesktopPathProvider
import java.nio.file.Path

class FileItemService: ClipItemService {

    companion object FileItemService {
        val FILE_BASE_PATH: Path = DesktopPathProvider.resolve(appFileType = AppFileType.FILE)
    }

}