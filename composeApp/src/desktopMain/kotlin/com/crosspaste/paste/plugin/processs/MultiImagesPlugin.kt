package com.crosspaste.paste.plugin.processs

import com.crosspaste.paste.item.FilesPasteItem
import com.crosspaste.paste.item.ImagesPasteItem
import com.crosspaste.paste.plugin.process.PasteProcessPlugin
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.realm.paste.PasteItem
import com.crosspaste.utils.getCodecsUtils
import com.crosspaste.utils.getJsonUtils
import io.realm.kotlin.MutableRealm
import io.realm.kotlin.ext.toRealmList
import kotlinx.serialization.encodeToString

class MultiImagesPlugin(private val userDataPathProvider: UserDataPathProvider) : PasteProcessPlugin {

    private val codecsUtils = getCodecsUtils()

    private val jsonUtils = getJsonUtils()

    override fun process(
        pasteItems: List<PasteItem>,
        realm: MutableRealm,
    ): List<PasteItem> {
        if (pasteItems.size <= 1) {
            return pasteItems
        } else {
            val relativePathList =
                pasteItems.map { it as ImagesPasteItem }.flatMap { it.relativePathList }
                    .toRealmList()
            val fileInfoMap =
                pasteItems.map { it as ImagesPasteItem }
                    .flatMap { it.getFileInfoTreeMap().entries }
                    .associate { it.key to it.value }
            val fileInfoMapJsonString = jsonUtils.JSON.encodeToString(fileInfoMap)
            val count = fileInfoMap.map { it.value.getCount() }.sum()
            val size = fileInfoMap.map { it.value.size }.sum()
            val hash =
                pasteItems.map { it as FilesPasteItem }.map { it.hash }
                    .toTypedArray().let { codecsUtils.hashByArray(it) }
            pasteItems.forEach { it.clear(realm, userDataPathProvider, clearResource = false) }
            return ImagesPasteItem().apply {
                this.relativePathList = relativePathList
                this.fileInfoTree = fileInfoMapJsonString
                this.count = count
                this.size = size
                this.hash = hash
            }.let { listOf(it) }
        }
    }
}
