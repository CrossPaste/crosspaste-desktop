package com.crosspaste.app

interface RatingPromptManager {

    fun trackAppLaunch()

    fun trackSignificantAction()

    fun checkAndShowRatingPrompt(): Boolean
}
