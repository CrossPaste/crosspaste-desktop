package com.crosspaste.paste

import com.crosspaste.app.AppInfo
import com.crosspaste.app.AppLaunchState
import com.crosspaste.db.paste.PasteDao
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.paste.item.PasteItem
import com.crosspaste.paste.item.TextPasteItem
import com.crosspaste.paste.item.UrlPasteItem
import com.crosspaste.utils.getCodecsUtils
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

abstract class GuidePasteDataService(
    private val appInfo: AppInfo,
    private val appLaunchState: AppLaunchState,
    private val copywriter: GlobalCopywriter,
    private val pasteDao: PasteDao,
    private val searchContentService: SearchContentService,
) {

    companion object {
        const val CROSSPASTE_GUIDE = "CrossPaste Guide"
    }

    private val codecsUtils = getCodecsUtils()

    abstract val guideKey: String

    fun isFirstLaunch(): Boolean = appLaunchState.firstLaunch

    fun saveData() {
        for (i in 5 downTo 0) {
            val pasteData = getGuidePasteData(i)
            pasteDao.createPasteData(pasteData)
        }
    }

    private fun buildJson(index: Int): JsonObject =
        buildJsonObject {
            put("guideIndex", index)
        }

    private fun getGuideIndexFromJson(jsonObject: JsonObject): Int? = jsonObject["guideIndex"]?.jsonPrimitive?.int

    fun updateData() {
        val pasteDataList = pasteDao.searchBySource(CROSSPASTE_GUIDE)
        if (pasteDataList.isNotEmpty()) {
            pasteDataList.forEach { pasteData ->
                pasteData.pasteAppearItem?.let { pasteItem ->
                    val oldSize = pasteItem.size
                    pasteItem.extraInfo?.let { extraInfo ->
                        getGuideIndexFromJson(extraInfo)?.let { index ->
                            val newPasteItem = getGuidePasteItem(index)
                            pasteDao.updatePasteAppearItem(
                                id = pasteData.id,
                                pasteItem = pasteItem,
                                pasteSearchContent =
                                    searchContentService.createSearchContent(
                                        pasteData.source,
                                        pasteItem.getSearchContent(),
                                    ),
                                addedSize = newPasteItem.size - oldSize,
                            )
                        }
                    }
                }
            }
        }
    }

    private fun getGuidePasteItem(index: Int): PasteItem {
        val extraInfo = buildJson(index)

        return if (index == 5) {
            val githubUrl = "https://github.com/CrossPaste/crosspaste-desktop"
            val githubUrlBytes = githubUrl.encodeToByteArray()
            val githubUrlHash = codecsUtils.hash(githubUrlBytes)
            val githubUrlSize = githubUrlBytes.size.toLong()
            UrlPasteItem(
                identifiers = listOf(),
                url = githubUrl,
                size = githubUrlSize,
                hash = githubUrlHash,
                extraInfo = extraInfo,
            )
        } else {
            val text = copywriter.getText("$guideKey$index").replace("\\n", "\n")
            val byteArray = text.encodeToByteArray()
            val size = byteArray.size.toLong()
            val hash = codecsUtils.hash(byteArray)
            TextPasteItem(
                identifiers = listOf(),
                text = text,
                size = size,
                hash = hash,
                extraInfo = extraInfo,
            )
        }
    }

    private fun getGuidePasteData(index: Int): PasteData =
        if (index == 5) {
            val githubUrl = "https://github.com/CrossPaste/crosspaste-desktop"
            val githubUrlBytes = githubUrl.encodeToByteArray()
            val githubUrlHash = codecsUtils.hash(githubUrlBytes)
            val githubUrlSize = githubUrlBytes.size.toLong()
            PasteData(
                appInstanceId = appInfo.appInstanceId,
                favorite = false,
                pasteAppearItem = getGuidePasteItem(index),
                pasteCollection = PasteCollection(listOf()),
                pasteType = PasteType.URL_TYPE.type,
                source = CROSSPASTE_GUIDE,
                size = githubUrlSize,
                hash = githubUrlHash,
                pasteState = PasteState.LOADED,
            )
        } else {
            val text = copywriter.getText("$guideKey$index").replace("\\n", "\n")
            val byteArray = text.encodeToByteArray()
            val size = byteArray.size.toLong()
            val hash = codecsUtils.hash(byteArray)

            PasteData(
                appInstanceId = appInfo.appInstanceId,
                favorite = false,
                pasteAppearItem = getGuidePasteItem(index),
                pasteCollection = PasteCollection(listOf()),
                pasteType = PasteType.TEXT_TYPE.type,
                source = CROSSPASTE_GUIDE,
                size = size,
                hash = hash,
                pasteState = PasteState.LOADED,
            )
        }

    fun initData() {
        if (isFirstLaunch()) {
            if (pasteDao.getSize(allOrFavorite = true) == 0L) {
                saveData()
            }
        }
    }
}
