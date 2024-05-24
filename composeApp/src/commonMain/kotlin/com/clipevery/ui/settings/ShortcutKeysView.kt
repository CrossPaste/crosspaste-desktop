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
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clipevery.LocalKoinApplication
import com.clipevery.i18n.GlobalCopywriter
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
    val current = LocalKoinApplication.current
    val copywriter = current.koin.get<GlobalCopywriter>()
    val shortcutKeys = current.koin.get<ShortcutKeys>()

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier =
                Modifier.fillMaxSize()
                    .background(MaterialTheme.colors.surface),
        ) {
            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier =
                    Modifier.fillMaxWidth()
                        .height(40.dp)
                        .padding(horizontal = 12.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                settingsText(copywriter.getText("Paste"))
                Spacer(modifier = Modifier.weight(1f))
                ShortcutKeyItemView(shortcutKeys.shortcutKeysCore.keys["Paste"] ?: emptyArray())
            }
        }
    }
}

@Composable
fun ShortcutKeyItemView(keys: Array<String>) {
    Row {
        keys.forEachIndexed { index, key ->
            KeyboardView(keyboardValue = key)
            if (index != key.length) {
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
