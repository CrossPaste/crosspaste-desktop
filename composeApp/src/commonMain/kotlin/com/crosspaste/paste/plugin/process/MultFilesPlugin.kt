package com.crosspaste.paste.plugin.process

import com.crosspaste.paste.item.FilesPasteItem
import com.crosspaste.paste.item.PasteItem
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.utils.getCodecsUtils
import com.crosspaste.utils.getJsonUtils
import io.realm.kotlin.MutableRealm
import io.realm.kotlin.ext.toRealmList
import kotlinx.serialization.encodeToString

class MultFilesPlugin(private val userDataPathProvider: UserDataPathProvider) : PasteProcessPlugin {

    private val codecsUtils = getCodecsUtils()

    private val jsonUtils = getJsonUtils()

    override fun process(
        pasteItems: List<PasteItem>,
        realm: MutableRealm,
        source: String?,
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
            val fileInfoMapJsonString = jsonUtils.JSON.encodeToString(fileInfoMap)
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
