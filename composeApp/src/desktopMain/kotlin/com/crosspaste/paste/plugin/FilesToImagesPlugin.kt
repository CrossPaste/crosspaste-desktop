package com.crosspaste.paste.plugin

import com.crosspaste.app.AppFileType
import com.crosspaste.dao.paste.PasteItem
import com.crosspaste.paste.PasteProcessPlugin
import com.crosspaste.paste.item.FilesPasteItem
import com.crosspaste.paste.item.ImagesPasteItem
import com.crosspaste.path.DesktopPathProvider
import com.crosspaste.utils.DesktopFileUtils
import com.crosspaste.utils.FileExtUtils.canPreviewImage
import com.crosspaste.utils.extension
import io.realm.kotlin.MutableRealm
import io.realm.kotlin.ext.toRealmList

object FilesToImagesPlugin : PasteProcessPlugin {

    private val fileBasePath = DesktopPathProvider.resolve(appFileType = AppFileType.FILE)
    private val imageBasePath = DesktopPathProvider.resolve(appFileType = AppFileType.IMAGE)

    override fun process(
        pasteItems: List<PasteItem>,
        realm: MutableRealm,
    ): List<PasteItem> {
        return pasteItems.map { pasteAppearItem ->
            if (pasteAppearItem is FilesPasteItem) {
                if (pasteAppearItem.getFilePaths().map { path -> path.extension }.all { canPreviewImage(it) }) {
                    pasteAppearItem.relativePathList.map {
                        val srcPath = DesktopPathProvider.resolve(fileBasePath, it, autoCreate = false, isFile = true)
                        val destPath = DesktopPathProvider.resolve(imageBasePath, it, autoCreate = true, isFile = true)
                        if (!DesktopFileUtils.moveFile(srcPath, destPath)) {
                            throw IllegalStateException("Failed to move file from $srcPath to $destPath")
                        }
                    }
                    val identifierList = pasteAppearItem.getIdentifierList().toRealmList()
                    val relativePathList = pasteAppearItem.relativePathList.toRealmList()
                    val fileInfoTree = pasteAppearItem.fileInfoTree
                    val count = pasteAppearItem.count
                    val size = pasteAppearItem.size
                    val md5 = pasteAppearItem.md5
                    pasteAppearItem.clear(realm, clearResource = false)
                    ImagesPasteItem().apply {
                        this.identifiers = identifierList
                        this.relativePathList = relativePathList
                        this.fileInfoTree = fileInfoTree
                        this.count = count
                        this.size = size
                        this.md5 = md5
                    }
                } else {
                    pasteAppearItem
                }
            } else {
                pasteAppearItem
            }
        }
    }
}
