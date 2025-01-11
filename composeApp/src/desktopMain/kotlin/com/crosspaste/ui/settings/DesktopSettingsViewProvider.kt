package com.crosspaste.ui.settings

import androidx.compose.runtime.Composable
import com.crosspaste.ui.base.ExpandView
import com.crosspaste.ui.base.clipboard
import com.crosspaste.ui.base.database
import com.crosspaste.ui.base.network

class DesktopSettingsViewProvider : SettingsViewProvider {

    @Composable
    override fun MainSettingsView() {
        MainSettingsContentView()
    }

    @Composable
    override fun NetSettingsView() {
        ExpandView(
            title = "network",
            icon = { network() },
        ) {
            NetSettingsContentView()
        }
    }

    @Composable
    override fun PasteboardSettingsView() {
        ExpandView(
            title = "pasteboard",
            icon = { clipboard() },
        ) {
            PasteboardSettingsContentView()
        }
    }

    @Composable
    override fun StoreSettingsView() {
        ExpandView(
            title = "store",
            icon = { database() },
        ) {
            StoreSettingsContentView()
        }
    }
}
