package com.clipevery.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clipevery.LocalKoinApplication
import com.clipevery.i18n.GlobalCopywriter
import com.clipevery.listener.KeyboardKeyInfo
import com.clipevery.listener.ShortcutKeys
import com.clipevery.ui.PageViewContext
import com.clipevery.ui.WindowDecoration
import com.clipevery.ui.base.KeyboardView

@Composable
fun ShortcutKeysView(currentPageViewContext: MutableState<PageViewContext>) {
    WindowDecoration(currentPageViewContext, "Shortcut_Keys")
    ShortcutKeysContentView()
}

@Composable
fun ShortcutKeysContentView() {
    val scrollState = rememberScrollState()

    Box(
        modifier =
            Modifier.fillMaxSize()
                .background(MaterialTheme.colors.surface),
    ) {
        Column(
            modifier =
                Modifier.verticalScroll(scrollState)
                    .fillMaxSize()
                    .background(MaterialTheme.colors.surface),
        ) {
            Spacer(modifier = Modifier.height(20.dp))

            Column(
                modifier =
                    Modifier.fillMaxSize()
                        .padding(horizontal = 16.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colors.background),
            ) {
                ShortcutKeyRow("Paste")
            }

            Spacer(modifier = Modifier.height(20.dp))

            Column(
                modifier =
                    Modifier.fillMaxSize()
                        .padding(horizontal = 16.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colors.background),
            ) {
                ShortcutKeyRow("Paste_Local_Last")

                Divider(modifier = Modifier.padding(start = 15.dp))

                ShortcutKeyRow("Paste_Remote_Last")
            }

            Spacer(modifier = Modifier.height(20.dp))

            Column(
                modifier =
                    Modifier.fillMaxSize()
                        .padding(horizontal = 16.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colors.background),
            ) {
                ShortcutKeyRow("ShowMain")

                Divider(modifier = Modifier.padding(start = 15.dp))

                ShortcutKeyRow("ShowSearch")
            }

            Spacer(modifier = Modifier.height(20.dp))

            Column(
                modifier =
                    Modifier.fillMaxSize()
                        .padding(horizontal = 16.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colors.background),
            ) {
                ShortcutKeyRow("SwitchMonitorPasteboard")

                Divider(modifier = Modifier.padding(start = 35.dp))

                ShortcutKeyRow("SwitchEncrypt")
            }
        }
    }
}

@Composable
fun ShortcutKeyRow(name: String) {
    val current = LocalKoinApplication.current
    val copywriter = current.koin.get<GlobalCopywriter>()
    val shortcutKeys = current.koin.get<ShortcutKeys>()
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .height(40.dp)
                .padding(horizontal = 12.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        settingsText(copywriter.getText(name))
        Spacer(modifier = Modifier.weight(1f))
        ShortcutKeyItemView(shortcutKeys.shortcutKeysCore.keys[name] ?: listOf())
    }
}

@Composable
fun ShortcutKeyItemView(keys: List<KeyboardKeyInfo>) {
    Row(
        modifier = Modifier.wrapContentSize(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        keys.forEachIndexed { index, info ->
            KeyboardView(keyboardValue = info.name, backgroundColor = MaterialTheme.colors.surface)
            if (index != keys.size - 1) {
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = "+",
                    style =
                        TextStyle(
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Light,
                            fontFamily = MaterialTheme.typography.body1.fontFamily,
                        ),
                )
                Spacer(modifier = Modifier.width(5.dp))
            }
        }
    }
}
