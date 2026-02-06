package com.crosspaste.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.ui.base.KeyboardView
import com.crosspaste.ui.base.enter
import com.crosspaste.ui.model.PasteSelectionViewModel
import com.crosspaste.ui.theme.AppUISize.tiny
import com.crosspaste.ui.theme.AppUISize.tinyRoundedCornerShape
import com.crosspaste.utils.GlobalCoroutineScope.mainCoroutineDispatcher
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
fun QuickPasteView() {
    val copywriter = koinInject<GlobalCopywriter>()
    val pasteSelectionViewModel = koinInject<PasteSelectionViewModel>()

    Row(
        modifier = Modifier.wrapContentSize(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .wrapContentSize()
                    .clip(tinyRoundedCornerShape)
                    .clickable {
                        mainCoroutineDispatcher.launch {
                            pasteSelectionViewModel.toPaste()
                        }
                    },
        ) {
            KeyboardView(key = enter)
        }
        Spacer(modifier = Modifier.width(tiny))
        Text(text = "/", color = MaterialTheme.colorScheme.onSurface)
        Spacer(modifier = Modifier.width(tiny))
        Box(
            modifier =
                Modifier
                    .wrapContentSize()
                    .clip(tinyRoundedCornerShape)
                    .clickable {
                        mainCoroutineDispatcher.launch {
                            pasteSelectionViewModel.toPaste()
                        }
                    },
        ) {
            KeyboardView(key = copywriter.getText("double_click"))
        }
    }
}
