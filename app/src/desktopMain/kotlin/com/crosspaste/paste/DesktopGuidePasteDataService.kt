package com.crosspaste.paste

import com.crosspaste.app.AppInfo
import com.crosspaste.app.AppLaunchState
import com.crosspaste.db.paste.PasteDaoApi
import com.crosspaste.i18n.GlobalCopywriter

class DesktopGuidePasteDataService(
    appInfo: AppInfo,
    appLaunchState: AppLaunchState,
    copywriter: GlobalCopywriter,
    pasteDao: PasteDaoApi,
    searchContentService: SearchContentService,
) : GuidePasteDataService(appInfo, appLaunchState, copywriter, pasteDao, searchContentService) {

    override val guideKey: String = "desktop_guide_"
}
