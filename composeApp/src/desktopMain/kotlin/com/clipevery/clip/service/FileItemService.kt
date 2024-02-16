package com.clipevery.clip.service

import com.clipevery.app.AppFileType
import com.clipevery.clip.ClipCollector
import com.clipevery.clip.ClipItemService
import com.clipevery.path.DesktopPathProvider
import java.awt.datatransfer.Transferable
import java.nio.file.Path

class FileItemService: ClipItemService {

    companion object FileItemService {
        val FILE_BASE_PATH: Path = DesktopPathProvider.resolve(appFileType = AppFileType.FILE)
    }

    override fun getIdentifiers(): List<String> {
        TODO("Not yet implemented")
    }

    override fun createClipItem(
        clipId: Int,
        itemIndex: Int,
        hpn: String?,
        transferable: Transferable,
        clipCollector: ClipCollector
    ) {
        TODO("Not yet implemented")
    }

}