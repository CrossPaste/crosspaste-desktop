package com.crosspaste.share

interface ShareService {

    val sharePlatformList: List<SharePlatform>

    val shareContentKey: String

    val shareTitleKey: String

    fun getShareText(): String

    fun getShareTitle(): String

    fun getShareContent(): String

    fun getShareUrl(): String
}
