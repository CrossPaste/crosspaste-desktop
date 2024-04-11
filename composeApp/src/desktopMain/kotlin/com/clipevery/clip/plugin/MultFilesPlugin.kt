package com.clipevery.clip.plugin

import com.clipevery.clip.ClipPlugin
import com.clipevery.clip.item.FilesClipItem
import com.clipevery.dao.clip.ClipAppearItem
import com.clipevery.utils.DesktopJsonUtils
import com.clipevery.utils.EncryptUtils.md5ByArray
import io.realm.kotlin.MutableRealm
import io.realm.kotlin.ext.toRealmList
import kotlinx.serialization.encodeToString

object MultFilesPlugin : ClipPlugin {
    override fun pluginProcess(
        clipAppearItems: List<ClipAppearItem>,
        realm: MutableRealm,
    ): List<ClipAppearItem> {
        if (clipAppearItems.size <= 1) {
            return clipAppearItems
        } else {
            val relativePathList =
                clipAppearItems.map { it as FilesClipItem }.flatMap { it.relativePathList }
                    .toRealmList()
            val fileInfoMap =
                clipAppearItems.map { it as FilesClipItem }
                    .flatMap { it.getFileInfoTreeMap().entries }
                    .associate { it.key to it.value }
            val fileInfoMapJsonString = DesktopJsonUtils.JSON.encodeToString(fileInfoMap)
            val md5 =
                clipAppearItems.map { it as FilesClipItem }.map { it.md5 }
                    .toTypedArray().let { md5ByArray(it) }
            clipAppearItems.forEach { it.clear(realm, clearResource = false) }
            return FilesClipItem().apply {
                this.relativePathList = relativePathList
                this.fileInfoTree = fileInfoMapJsonString
                this.md5 = md5
            }.let { listOf(it) }
        }
    }
}
