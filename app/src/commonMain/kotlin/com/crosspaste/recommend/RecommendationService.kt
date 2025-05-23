package com.crosspaste.recommend

interface RecommendationService {

    val recommendPlatformList: List<RecommendationPlatform>

    val recommendContentKey: String

    val recommendTitleKey: String

    fun getRecommendText(): String

    fun getRecommendTitle(): String

    fun getRecommendContent(): String

    fun getRecommendUrl(): String
}
