package com.crosspaste.paste

import com.crosspaste.app.AppInfo
import com.crosspaste.app.AppLaunchState
import com.crosspaste.db.paste.PasteDao
import com.crosspaste.i18n.GlobalCopywriter

class DesktopInitPasteDataService(
    appInfo: AppInfo,
    appLaunchState: AppLaunchState,
    copywriter: GlobalCopywriter,
    pasteDao: PasteDao,
) : InitPasteDataService(appInfo, appLaunchState, copywriter, pasteDao) {

    override val guideKey: String = "desktop_guide_"
}
