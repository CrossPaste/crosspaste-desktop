package com.clipevery.clip.plugin

import com.clipevery.app.AppFileType
import com.clipevery.clip.ClipPlugin
import com.clipevery.clip.item.FilesClipItem
import com.clipevery.clip.item.ImagesClipItem
import com.clipevery.dao.clip.ClipAppearItem
import com.clipevery.path.DesktopPathProvider
import com.clipevery.utils.DesktopFileUtils
import com.clipevery.utils.FileExtUtils.canPreviewImage
import io.ktor.util.*
import io.realm.kotlin.MutableRealm
import io.realm.kotlin.ext.toRealmList

object ConvertImagePlugin: ClipPlugin {
    override fun pluginProcess(clipAppearItems: List<ClipAppearItem>, realm: MutableRealm): List<ClipAppearItem> {
        return clipAppearItems.map { appearItem ->
            if (appearItem is FilesClipItem && appearItem.getFilePaths().map { it.extension }
                    .all { canPreviewImage(it) }) {
                val fileBasePath = DesktopPathProvider.resolve(appFileType = AppFileType.FILE)
                val imageBasePath = DesktopPathProvider.resolve(appFileType = AppFileType.IMAGE)

                appearItem.relativePathList.map {
                    val srcPath = DesktopPathProvider.resolve(fileBasePath, it, autoCreate = false)
                    val destPath = DesktopPathProvider.resolve(imageBasePath, it, autoCreate = true)
                    if (DesktopFileUtils.moveFile(srcPath, destPath)) {
                        throw IllegalStateException("Failed to move file from $srcPath to $destPath")
                    }
                }
                appearItem.clear(realm)
                ImagesClipItem().apply {
                    this.identifierList = appearItem.getIdentifiers().toRealmList()
                    this.relativePathList = appearItem.relativePathList.toRealmList()
                    this.md5List = appearItem.getFileMd5List().toRealmList()
                    this.md5 = appearItem.md5
                }
            } else {
                appearItem
            }
        }
    }
}