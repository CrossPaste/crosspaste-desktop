package com.crosspaste.ui.settings

import androidx.compose.runtime.Composable
import com.crosspaste.ui.base.BaseViewProvider
import com.crosspaste.ui.base.clipboard
import com.crosspaste.ui.base.database
import com.crosspaste.ui.base.network

class DesktopSettingsViewProvider(
    private val baseViewProvider: BaseViewProvider,
) : SettingsViewProvider {

    @Composable
    override fun MainSettingsView() {
        MainSettingsContentView()
    }

    @Composable
    override fun NetSettingsView() {
        baseViewProvider.ExpandView(
            title = "network",
            icon = { network() },
        ) {
            NetSettingsContentView()
        }
    }

    @Composable
    override fun PasteboardSettingsView() {
        baseViewProvider.ExpandView(
            title = "pasteboard",
            icon = { clipboard() },
        ) {
            PasteboardSettingsContentView()
        }
    }

    @Composable
    override fun StoreSettingsView() {
        baseViewProvider.ExpandView(
            title = "store",
            icon = { database() },
        ) {
            StoreSettingsContentView()
        }
    }
}
