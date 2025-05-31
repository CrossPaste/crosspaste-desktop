package com.crosspaste.ui.base

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.ui.theme.AppUIColors
import com.crosspaste.ui.theme.AppUIFont.dialogButtonTextStyle
import com.crosspaste.ui.theme.AppUISize.medium
import com.crosspaste.ui.theme.AppUISize.small2X
import com.crosspaste.ui.theme.AppUISize.tiny6X
import org.koin.compose.koinInject

@Composable
fun DialogButtonsView(
    cancelTitle: String = "cancel",
    confirmTitle: String = "confirm",
    cancelAction: () -> Unit,
    confirmAction: () -> Unit,
) {
    val copywriter = koinInject<GlobalCopywriter>()
    Row(
        modifier =
            Modifier.padding(top = medium)
                .fillMaxWidth()
                .wrapContentHeight(),
        horizontalArrangement = Arrangement.spacedBy(small2X),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Card(
            onClick = cancelAction,
            modifier = Modifier.weight(1f),
            colors =
                androidx.compose.material3.CardDefaults.cardColors(
                    containerColor = AppUIColors.dialogBackground,
                ),
            border =
                BorderStroke(
                    width = tiny6X,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.72f),
                ),
        ) {
            Text(
                text = copywriter.getText(cancelTitle),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = medium, vertical = small2X),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = dialogButtonTextStyle,
                color = MaterialTheme.colorScheme.contentColorFor(AppUIColors.dialogBackground),
            )
        }

        Card(
            onClick = confirmAction,
            modifier = Modifier.weight(1f),
            colors =
                androidx.compose.material3.CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                ),
        ) {
            Text(
                text = copywriter.getText(confirmTitle),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = medium, vertical = small2X),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = dialogButtonTextStyle,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}
