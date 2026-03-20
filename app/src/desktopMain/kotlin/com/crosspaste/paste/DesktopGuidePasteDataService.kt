package com.crosspaste.paste

import com.crosspaste.app.AppInfo
import com.crosspaste.app.AppLaunchState
import com.crosspaste.db.paste.PasteDao
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.paste.item.PasteItemReader

class DesktopGuidePasteDataService(
    appInfo: AppInfo,
    appLaunchState: AppLaunchState,
    copywriter: GlobalCopywriter,
    pasteDao: PasteDao,
    pasteItemReader: PasteItemReader,
    searchContentService: SearchContentService,
) : GuidePasteDataService(appInfo, appLaunchState, copywriter, pasteDao, pasteItemReader, searchContentService) {

    override val guideKey: String = "desktop_guide_"
}
