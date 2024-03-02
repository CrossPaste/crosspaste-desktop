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
                    val srcPath = DesktopPathProvider.resolve(fileBasePath, it, autoCreate = false, isFile = true)
                    val destPath = DesktopPathProvider.resolve(imageBasePath, it, autoCreate = true, isFile = true)
                    if (!DesktopFileUtils.moveFile(srcPath, destPath)) {
                        throw IllegalStateException("Failed to move file from $srcPath to $destPath")
                    }
                }
                val identifierList = appearItem.getIdentifiers().toRealmList()
                val relativePathList = appearItem.relativePathList.toRealmList()
                val md5List = appearItem.getFileMd5List().toRealmList()
                val md5 = appearItem.md5
                appearItem.clear(realm)
                ImagesClipItem().apply {
                    this.identifierList = identifierList
                    this.relativePathList = relativePathList
                    this.md5List = md5List
                    this.md5 = md5
                }
            } else {
                appearItem
            }
        }
    }
}