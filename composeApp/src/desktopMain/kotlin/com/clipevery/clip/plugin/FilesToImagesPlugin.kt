package com.clipevery.clip.plugin

import com.clipevery.app.AppFileType
import com.clipevery.clip.ClipPlugin
import com.clipevery.clip.item.FilesClipItem
import com.clipevery.clip.item.ImagesClipItem
import com.clipevery.dao.clip.ClipItem
import com.clipevery.path.DesktopPathProvider
import com.clipevery.utils.DesktopFileUtils
import com.clipevery.utils.FileExtUtils.canPreviewImage
import io.ktor.util.*
import io.realm.kotlin.MutableRealm
import io.realm.kotlin.ext.toRealmList

object FilesToImagesPlugin : ClipPlugin {

    private val fileBasePath = DesktopPathProvider.resolve(appFileType = AppFileType.FILE)
    private val imageBasePath = DesktopPathProvider.resolve(appFileType = AppFileType.IMAGE)

    override fun pluginProcess(
        clipItems: List<ClipItem>,
        realm: MutableRealm,
    ): List<ClipItem> {
        return clipItems.map { clipAppearItem ->
            if (clipAppearItem is FilesClipItem) {
                if (clipAppearItem.getFilePaths().map { path -> path.extension }.all { canPreviewImage(it) }) {
                    clipAppearItem.relativePathList.map {
                        val srcPath = DesktopPathProvider.resolve(fileBasePath, it, autoCreate = false, isFile = true)
                        val destPath = DesktopPathProvider.resolve(imageBasePath, it, autoCreate = true, isFile = true)
                        if (!DesktopFileUtils.moveFile(srcPath, destPath)) {
                            throw IllegalStateException("Failed to move file from $srcPath to $destPath")
                        }
                    }
                    val identifierList = clipAppearItem.getIdentifierList().toRealmList()
                    val relativePathList = clipAppearItem.relativePathList.toRealmList()
                    val fileInfoTree = clipAppearItem.fileInfoTree
                    val count = clipAppearItem.count
                    val size = clipAppearItem.size
                    val md5 = clipAppearItem.md5
                    clipAppearItem.clear(realm, clearResource = false)
                    ImagesClipItem().apply {
                        this.identifiers = identifierList
                        this.relativePathList = relativePathList
                        this.fileInfoTree = fileInfoTree
                        this.count = count
                        this.size = size
                        this.md5 = md5
                    }
                } else {
                    clipAppearItem
                }
            } else {
                clipAppearItem
            }
        }
    }
}
