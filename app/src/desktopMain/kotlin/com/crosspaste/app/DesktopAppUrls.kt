package com.crosspaste.app

import com.crosspaste.utils.DesktopResourceUtils

object DesktopAppUrls : AppUrls {

    private val appUrlsProperties = DesktopResourceUtils.loadProperties("app-urls.properties")

    override val homeUrl: String = appUrlsProperties.getProperty("home-url")

    override val changeLogUrl: String = appUrlsProperties.getProperty("change-log-url")

    override val checkMetadataUrl: String = appUrlsProperties.getProperty("check-metadata-url")

    override val issueTrackerUrl: String = appUrlsProperties.getProperty("issue-tracker-url")
}
