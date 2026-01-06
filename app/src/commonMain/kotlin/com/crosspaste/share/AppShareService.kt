package com.crosspaste.share

interface AppShareService {

    val appSharePlatformList: List<AppSharePlatform>

    val shareContentKey: String

    val shareTitleKey: String

    fun getShareText(): String

    fun getShareTitle(): String

    fun getShareContent(): String

    fun getShareUrl(): String
}
