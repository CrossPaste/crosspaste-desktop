package com.crosspaste.paste.plugin.processs

import com.crosspaste.app.AppFileType
import com.crosspaste.dao.paste.PasteItem
import com.crosspaste.paste.item.FilesPasteItem
import com.crosspaste.paste.item.ImagesPasteItem
import com.crosspaste.paste.plugin.process.PasteProcessPlugin
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.utils.DesktopFileUtils
import com.crosspaste.utils.FileExtUtils.canPreviewImage
import com.crosspaste.utils.extension
import io.realm.kotlin.MutableRealm
import io.realm.kotlin.ext.toRealmList

class FilesToImagesPlugin(
    private val userDataPathProvider: UserDataPathProvider,
) : PasteProcessPlugin {

    private val fileBasePath = userDataPathProvider.resolve(appFileType = AppFileType.FILE)
    private val imageBasePath = userDataPathProvider.resolve(appFileType = AppFileType.IMAGE)

    override fun process(
        pasteItems: List<PasteItem>,
        realm: MutableRealm,
    ): List<PasteItem> {
        return pasteItems.map { pasteAppearItem ->
            if (pasteAppearItem is FilesPasteItem) {
                if (pasteAppearItem.getFilePaths(userDataPathProvider).map { path -> path.extension }.all { canPreviewImage(it) }) {
                    pasteAppearItem.relativePathList.map {
                        val srcPath = userDataPathProvider.resolve(fileBasePath, it, autoCreate = false, isFile = true)
                        val destPath = userDataPathProvider.resolve(imageBasePath, it, autoCreate = true, isFile = true)
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
                    pasteAppearItem.clear(realm, userDataPathProvider, clearResource = false)
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
