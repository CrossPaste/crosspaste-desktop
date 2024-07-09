package com.crosspaste.paste.plugin

import com.crosspaste.dao.paste.PasteItem
import com.crosspaste.paste.PastePlugin
import com.crosspaste.paste.item.FilesPasteItem
import com.crosspaste.utils.DesktopJsonUtils
import com.crosspaste.utils.getCodecsUtils
import io.realm.kotlin.MutableRealm
import io.realm.kotlin.ext.toRealmList
import kotlinx.serialization.encodeToString

object MultFilesPlugin : PastePlugin {

    private val codecsUtils = getCodecsUtils()

    override fun pluginProcess(
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
            val md5 =
                pasteItems.map { it as FilesPasteItem }.map { it.md5 }
                    .toTypedArray().let { codecsUtils.md5ByArray(it) }
            pasteItems.forEach { it.clear(realm, clearResource = false) }
            return FilesPasteItem().apply {
                this.relativePathList = relativePathList
                this.fileInfoTree = fileInfoMapJsonString
                this.md5 = md5
            }.let { listOf(it) }
        }
    }
}
