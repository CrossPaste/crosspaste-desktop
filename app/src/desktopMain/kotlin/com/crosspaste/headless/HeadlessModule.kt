package com.crosspaste.headless

import com.crosspaste.app.AppTokenApi
import com.crosspaste.app.AppWindowManager
import com.crosspaste.app.DesktopRatingPromptManager
import com.crosspaste.app.RatingPromptManager
import com.crosspaste.i18n.DesktopGlobalCopywriter
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.notification.NotificationManager
import com.crosspaste.sound.SoundService
import com.crosspaste.sync.TokenCache
import com.crosspaste.sync.TokenCacheApi
import com.crosspaste.ui.base.UISupport
import org.koin.dsl.module

fun headlessUiModule() =
    module {
        single<AppTokenApi> { HeadlessAppTokenService() }
        single<AppWindowManager> { HeadlessAppWindowManager() }
        single<GlobalCopywriter> { DesktopGlobalCopywriter(get(), lazy { get() }, get()) }
        single<NotificationManager> { HeadlessNotificationManager(get()) }
        single<RatingPromptManager> { DesktopRatingPromptManager() }
        single<SoundService> { HeadlessSoundService() }
        single<TokenCacheApi> { TokenCache }
        single<UISupport> { HeadlessUISupport() }
    }

fun headlessViewModelModule() = module {}
