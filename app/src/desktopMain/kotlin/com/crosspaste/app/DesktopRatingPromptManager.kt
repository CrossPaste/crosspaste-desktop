package com.crosspaste.app

class DesktopRatingPromptManager : RatingPromptManager {
    override fun trackAppLaunch() {
        // do nothing
    }

    override fun trackSignificantAction() {
        // do nothing
    }

    override fun checkAndShowRatingPrompt(): Boolean = false
}
