package com.crosspaste.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import com.crosspaste.app.AppInfo
import com.crosspaste.app.AppWindowManager
import com.crosspaste.platform.Platform
import com.crosspaste.ui.About
import com.crosspaste.ui.base.ExpandViewProvider
import com.crosspaste.ui.base.clipboard
import com.crosspaste.ui.base.database
import com.crosspaste.ui.base.info
import com.crosspaste.ui.base.network
import com.crosspaste.ui.theme.AppUISize.medium
import com.crosspaste.ui.theme.AppUISize.tiny2X
import com.crosspaste.ui.theme.AppUISize.tiny2XRoundedCornerShape

class DesktopSettingsViewProvider(
    private val appInfo: AppInfo,
    private val appWindowManager: AppWindowManager,
    private val expandViewProvider: ExpandViewProvider,
    private val platform: Platform,
) : SettingsViewProvider {

    @Composable
    override fun AboutItemView() {
        SettingItemView(
            text = "about",
            painter = info(),
        ) {
            Row(
                modifier =
                    Modifier
                        .clip(tiny2XRoundedCornerShape)
                        .clickable(onClick = {
                            appWindowManager.toScreen(About)
                        })
                        .padding(tiny2X),
            ) {
                SettingsText(
                    text = "CrossPaste ${appInfo.displayVersion()}",
                )
            }
        }
    }

    @Composable
    override fun MainSettingsView() {
        MainSettingsContentView()
    }

    @Composable
    override fun NetSettingsView() {
        expandViewProvider.ExpandView(
            barContent = { iconScale ->
                expandViewProvider.ExpandBarView(
                    title = "network",
                    icon = { network() },
                    iconScale = iconScale,
                )
            },
        ) {
            NetSettingsContentView()
        }
    }

    @Composable
    override fun PasteboardSettingsView() {
        expandViewProvider.ExpandView(
            barContent = { iconScale ->
                expandViewProvider.ExpandBarView(
                    title = "pasteboard",
                    icon = { clipboard() },
                    iconScale = iconScale,
                )
            },
        ) {
            PasteboardSettingsContentView {
                val isWindows by remember { mutableStateOf(platform.isWindows()) }
                if (isWindows) {
                    WindowsPasteboardSettingsContentView()
                }
            }
        }
    }

    @Composable
    override fun StoreSettingsView() {
        expandViewProvider.ExpandView(
            barContent = { iconScale ->
                expandViewProvider.ExpandBarView(
                    title = "store",
                    icon = { database() },
                    iconScale = iconScale,
                )
            },
        ) {
            StoreSettingsContentView {
                SetStoragePathView()
            }
        }
    }

    @Composable
    override fun SettingsCoreView() {
        MainSettingsView()

        Spacer(modifier = Modifier.height(medium))

        NetSettingsView()

        Spacer(modifier = Modifier.height(medium))

        StoreSettingsView()

        Spacer(modifier = Modifier.height(medium))

        PasteboardSettingsView()
    }
}
