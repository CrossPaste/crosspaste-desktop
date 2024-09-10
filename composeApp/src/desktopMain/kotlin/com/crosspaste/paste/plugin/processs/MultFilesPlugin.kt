package com.crosspaste.paste.plugin.processs

import com.crosspaste.paste.item.FilesPasteItem
import com.crosspaste.paste.plugin.process.PasteProcessPlugin
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.realm.paste.PasteItem
import com.crosspaste.utils.DesktopJsonUtils
import com.crosspaste.utils.getCodecsUtils
import io.realm.kotlin.MutableRealm
import io.realm.kotlin.ext.toRealmList
import kotlinx.serialization.encodeToString

class MultFilesPlugin(private val userDataPathProvider: UserDataPathProvider) : PasteProcessPlugin {

    private val codecsUtils = getCodecsUtils()

    override fun process(
        pasteItems: List<PasteItem>,
        realm: MutableRealm,
    ): List<PasteItem> {
        if (pasteItems.size <= 1) {
            return pasteItems
        } else {
            val relativePathList =
                pasteItems.map { it as FilesPasteItem }.flatMap { it.relativePathList }
                    .toRealmList()
            val fileInfoMap =
                pasteItems.map { it as FilesPasteItem }
                    .flatMap { it.getFileInfoTreeMap().entries }
                    .associate { it.key to it.value }
            val fileInfoMapJsonString = DesktopJsonUtils.JSON.encodeToString(fileInfoMap)
            val hash =
                pasteItems.map { it as FilesPasteItem }.map { it.hash }
                    .toTypedArray().let { codecsUtils.hashByArray(it) }
            pasteItems.forEach { it.clear(realm, userDataPathProvider, clearResource = false) }
            return FilesPasteItem().apply {
                this.relativePathList = relativePathList
                this.fileInfoTree = fileInfoMapJsonString
                this.hash = hash
            }.let { listOf(it) }
        }
    }
}
