package com.crosspaste.paste

import com.crosspaste.app.AppInfo
import com.crosspaste.app.DesktopAppLaunchState
import com.crosspaste.db.paste.PasteCollection
import com.crosspaste.db.paste.PasteDao
import com.crosspaste.db.paste.PasteData
import com.crosspaste.db.paste.PasteState
import com.crosspaste.db.paste.PasteType
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.paste.item.TextPasteItem
import com.crosspaste.paste.item.UrlPasteItem
import com.crosspaste.utils.getCodecsUtils

class DesktopInitPasteDataService(
    private val appInfo: AppInfo,
    private val appLaunchState: DesktopAppLaunchState,
    private val copywriter: GlobalCopywriter,
    pasteDao: PasteDao,
) : InitPasteDataService(pasteDao) {

    private val codecsUtils = getCodecsUtils()

    override fun isFirstLaunch(): Boolean {
        return appLaunchState.firstLaunch
    }

    override fun saveData() {
        val githubUrl = "https://github.com/CrossPaste/crosspaste-desktop"
        val githubUrlBytes = githubUrl.encodeToByteArray()
        val githubUrlHash = codecsUtils.hash(githubUrlBytes)
        val githubUrlSize = githubUrlBytes.size.toLong()
        val githubUrlPasteData =
            PasteData(
                appInstanceId = appInfo.appInstanceId,
                favorite = false,
                pasteAppearItem =
                    UrlPasteItem(
                        identifiers = listOf(),
                        url = githubUrl,
                        size = githubUrlSize,
                        hash = githubUrlHash,
                    ),
                pasteCollection = PasteCollection(listOf()),
                pasteType = PasteType.URL_TYPE.type,
                source = null,
                size = githubUrlSize,
                hash = githubUrlHash,
                pasteState = PasteState.LOADED,
            )
        pasteDao.createPasteData(githubUrlPasteData)
        for (i in 4 downTo 0) {
            val text = copywriter.getText("guide_$i").replace("\\n", "\n")
            val byteArray = text.encodeToByteArray()
            val size = byteArray.size.toLong()
            val hash = codecsUtils.hash(byteArray)

            val pasteData =
                PasteData(
                    appInstanceId = appInfo.appInstanceId,
                    favorite = false,
                    pasteAppearItem =
                        TextPasteItem(
                            identifiers = listOf(),
                            text = text,
                            size = size,
                            hash = hash,
                        ),
                    pasteCollection = PasteCollection(listOf()),
                    pasteType = PasteType.TEXT_TYPE.type,
                    source = null,
                    size = size,
                    hash = hash,
                    pasteState = PasteState.LOADED,
                )
            pasteDao.createPasteData(pasteData)
        }
    }
}
