package com.crosspaste

import coil3.PlatformContext
import com.crosspaste.app.AppFileChooser
import com.crosspaste.app.AppSize
import com.crosspaste.app.AppTokenApi
import com.crosspaste.app.AppWindowManager
import com.crosspaste.app.DesktopAppFileChooser
import com.crosspaste.app.DesktopAppSize
import com.crosspaste.app.DesktopAppTokenService
import com.crosspaste.app.DesktopAppWindowManager
import com.crosspaste.app.DesktopRatingPromptManager
import com.crosspaste.app.RatingPromptManager
import com.crosspaste.app.getDesktopAppWindowManager
import com.crosspaste.i18n.DesktopGlobalCopywriter
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.image.DesktopIconColorExtractor
import com.crosspaste.listener.ActiveGraphicsDevice
import com.crosspaste.listener.DesktopGlobalListener
import com.crosspaste.listener.DesktopShortKeysAction
import com.crosspaste.listener.DesktopShortcutKeys
import com.crosspaste.listener.DesktopShortcutKeysListener
import com.crosspaste.listener.DesktopShortcutKeysLoader
import com.crosspaste.listener.GlobalListener
import com.crosspaste.listener.ShortcutKeys
import com.crosspaste.listener.ShortcutKeysAction
import com.crosspaste.listener.ShortcutKeysListener
import com.crosspaste.listener.ShortcutKeysLoader
import com.crosspaste.notification.NotificationManager
import com.crosspaste.sound.DesktopSoundService
import com.crosspaste.sound.SoundService
import com.crosspaste.sync.TokenCache
import com.crosspaste.sync.TokenCacheApi
import com.crosspaste.ui.DesktopScreenProvider
import com.crosspaste.ui.NavigationManager
import com.crosspaste.ui.ScreenProvider
import com.crosspaste.ui.base.DesktopIconStyle
import com.crosspaste.ui.base.DesktopNotificationManager
import com.crosspaste.ui.base.DesktopUISupport
import com.crosspaste.ui.base.IconStyle
import com.crosspaste.ui.base.MenuHelper
import com.crosspaste.ui.base.SmartImageDisplayStrategy
import com.crosspaste.ui.base.UISupport
import com.crosspaste.ui.devices.DesktopDeviceScopeFactory
import com.crosspaste.ui.devices.DesktopSyncScopeFactory
import com.crosspaste.ui.devices.DeviceScopeFactory
import com.crosspaste.ui.devices.SyncScopeFactory
import com.crosspaste.ui.settings.DesktopStoragePathManager
import com.crosspaste.ui.settings.StoragePathManager
import com.crosspaste.ui.theme.DesktopThemeDetector
import com.crosspaste.ui.theme.ThemeDetector
import com.github.kwhat.jnativehook.keyboard.NativeKeyListener
import com.github.kwhat.jnativehook.mouse.NativeMouseListener
import org.koin.core.module.Module
import org.koin.dsl.module

fun desktopUiModule(): Module =
    module {
        single<ActiveGraphicsDevice> { get<DesktopAppSize>() }
        single<AppFileChooser> { DesktopAppFileChooser(get()) }
        single<AppSize> { get<DesktopAppSize>() }
        single<AppTokenApi> { DesktopAppTokenService(get(), get()) }
        single<AppWindowManager> { get<DesktopAppWindowManager>() }
        single<DesktopAppSize> { DesktopAppSize(get()) }
        single<DesktopAppWindowManager> {
            getDesktopAppWindowManager(get(), get(), lazy { get() }, lazy { get() }, get(), get())
        }
        single<DesktopIconColorExtractor> { DesktopIconColorExtractor(get()) }
        single<DesktopScreenProvider> { DesktopScreenProvider(get(), get(), get(), get(), get(), get()) }
        single<DesktopShortcutKeysListener> { DesktopShortcutKeysListener(get(), get()) }
        single<DeviceScopeFactory> { DesktopDeviceScopeFactory() }
        single<GlobalCopywriter> { DesktopGlobalCopywriter(get(), lazy { get() }, get()) }
        single<GlobalListener> { DesktopGlobalListener(get(), get(), get(), get()) }
        single<IconStyle> { DesktopIconStyle(get()) }
        single<MenuHelper> { MenuHelper(get(), get(), get(), get(), get()) }
        single<NavigationManager> { get<DesktopScreenProvider>() }
        single<NativeKeyListener> { get<DesktopShortcutKeysListener>() }
        single<NativeMouseListener> { get<DesktopAppSize>() }
        single<NotificationManager> { DesktopNotificationManager(get(), get(), get(), get()) }
        single<PlatformContext> { PlatformContext.INSTANCE }
        single<RatingPromptManager> { DesktopRatingPromptManager() }
        single<ScreenProvider> { get<DesktopScreenProvider>() }
        single<ShortcutKeys> { DesktopShortcutKeys(get(), get(), get()) }
        single<ShortcutKeysAction> {
            DesktopShortKeysAction(get(), get(), get(), get(), get(), get(), get())
        }
        single<ShortcutKeysListener> { get<DesktopShortcutKeysListener>() }
        single<ShortcutKeysLoader> { DesktopShortcutKeysLoader(get(), get()) }
        single<SmartImageDisplayStrategy> { SmartImageDisplayStrategy() }
        single<SoundService> { DesktopSoundService(get()) }
        single<StoragePathManager> { DesktopStoragePathManager() }
        single<SyncScopeFactory> { DesktopSyncScopeFactory() }
        single<ThemeDetector> { DesktopThemeDetector(get()) }
        single<TokenCacheApi> { TokenCache }
        single<UISupport> {
            DesktopUISupport(
                get(),
                get(),
                get(),
                get(),
                get(),
                get(),
                get<DesktopAppWindowManager>(),
            )
        }
    }
