package com.crosspaste.app

import com.crosspaste.utils.DesktopResourceUtils

object DesktopAppUrls : AppUrls {

    private val appUrlsProperties = DesktopResourceUtils.loadProperties("app-urls.properties")

    override val homeUrl: String = appUrlsProperties.getProperty("home-url")

    override val changeLogUrl: String = appUrlsProperties.getProperty("change-log-url")

    override val checkMetadataUrl: String = appUrlsProperties.getProperty("check-metadata-url")

    // Desktop-only: fallback version endpoint when GitHub ([checkMetadataUrl] host)
    // is unreachable, e.g. for users in mainland China. Intentionally NOT on the
    // shared [AppUrls] interface — mobile (which copies commonMain) does not use it.
    val versionApiUrl: String = appUrlsProperties.getProperty("version-api-url")

    override val issueTrackerUrl: String = appUrlsProperties.getProperty("issue-tracker-url")
}
