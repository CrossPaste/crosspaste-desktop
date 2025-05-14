package com.crosspaste.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.crosspaste.app.AppInfo
import com.crosspaste.app.AppWindowManager
import com.crosspaste.ui.About
import com.crosspaste.ui.base.ExpandViewProvider
import com.crosspaste.ui.base.clipboard
import com.crosspaste.ui.base.database
import com.crosspaste.ui.base.info
import com.crosspaste.ui.base.network

class DesktopSettingsViewProvider(
    private val appInfo: AppInfo,
    private val appWindowManager: AppWindowManager,
    private val expandViewProvider: ExpandViewProvider,
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
                        .clip(RoundedCornerShape(5.dp))
                        .clickable(onClick = {
                            appWindowManager.toScreen(About)
                        })
                        .padding(horizontal = 5.dp, vertical = 5.dp),
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
            title = "network",
            icon = { network() },
        ) {
            NetSettingsContentView()
        }
    }

    @Composable
    override fun PasteboardSettingsView() {
        expandViewProvider.ExpandView(
            title = "pasteboard",
            icon = { clipboard() },
        ) {
            PasteboardSettingsContentView()
        }
    }

    @Composable
    override fun StoreSettingsView() {
        expandViewProvider.ExpandView(
            title = "store",
            icon = { database() },
        ) {
            StoreSettingsContentView {
                SetStoragePathView()
            }
        }
    }

    @Composable
    override fun SettingsCoreView() {
        MainSettingsView()

        Spacer(modifier = Modifier.height(25.dp))

        NetSettingsView()

        Spacer(modifier = Modifier.height(25.dp))

        StoreSettingsView()

        Spacer(modifier = Modifier.height(25.dp))

        PasteboardSettingsView()
    }
}
