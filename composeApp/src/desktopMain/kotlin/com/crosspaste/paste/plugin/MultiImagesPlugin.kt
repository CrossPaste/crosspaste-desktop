package com.crosspaste.paste.plugin

import com.crosspaste.dao.paste.PasteItem
import com.crosspaste.paste.PasteProcessPlugin
import com.crosspaste.paste.item.FilesPasteItem
import com.crosspaste.paste.item.ImagesPasteItem
import com.crosspaste.utils.DesktopJsonUtils
import com.crosspaste.utils.getCodecsUtils
import io.realm.kotlin.MutableRealm
import io.realm.kotlin.ext.toRealmList
import kotlinx.serialization.encodeToString

object MultiImagesPlugin : PasteProcessPlugin {

    private val codecsUtils = getCodecsUtils()

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
            val fileInfoMapJsonString = DesktopJsonUtils.JSON.encodeToString(fileInfoMap)
            val count = fileInfoMap.map { it.value.getCount() }.sum()
            val size = fileInfoMap.map { it.value.size }.sum()
            val md5 =
                pasteItems.map { it as FilesPasteItem }.map { it.md5 }
                    .toTypedArray().let { codecsUtils.md5ByArray(it) }
            pasteItems.forEach { it.clear(realm, clearResource = false) }
            return ImagesPasteItem().apply {
                this.relativePathList = relativePathList
                this.fileInfoTree = fileInfoMapJsonString
                this.count = count
                this.size = size
                this.md5 = md5
            }.let { listOf(it) }
        }
    }
}
