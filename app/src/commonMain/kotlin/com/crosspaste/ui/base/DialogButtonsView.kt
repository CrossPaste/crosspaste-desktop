package com.crosspaste.ui.base

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.ui.theme.AppUIColors
import com.crosspaste.ui.theme.AppUIFont.dialogButtonTextStyle
import com.crosspaste.ui.theme.AppUISize
import com.crosspaste.ui.theme.AppUISize.medium
import com.crosspaste.ui.theme.AppUISize.small2X
import com.crosspaste.ui.theme.AppUISize.tiny6X
import org.koin.compose.koinInject

@Composable
fun DialogButtonsView(
    cancelTitle: String = "cancel",
    confirmTitle: String = "confirm",
    cancelEnabled: Boolean = true,
    confirmEnabled: Boolean = true,
    cancelAction: () -> Unit,
    confirmAction: () -> Unit,
) {
    val copywriter = koinInject<GlobalCopywriter>()
    Row(
        modifier =
            Modifier
                .padding(top = medium)
                .fillMaxWidth()
                .wrapContentHeight(),
        horizontalArrangement = Arrangement.spacedBy(small2X),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedButton(
            onClick = cancelAction,
            enabled = cancelEnabled,
            modifier = Modifier.weight(1f),
            shape = AppUISize.smallRoundedCornerShape,
            colors =
                ButtonDefaults.outlinedButtonColors(
                    containerColor = AppUIColors.generalBackground,
                    contentColor =
                        MaterialTheme.colorScheme.contentColorFor(
                            AppUIColors.generalBackground,
                        ),
                ),
            border =
                BorderStroke(
                    width = tiny6X,
                    color =
                        if (cancelEnabled) {
                            AppUIColors.mediumBorderColor
                        } else {
                            AppUIColors.mediumBorderColor.copy(alpha = 0.12f)
                        },
                ),
            contentPadding = PaddingValues(horizontal = medium, vertical = small2X),
        ) {
            Text(
                text = copywriter.getText(cancelTitle),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = dialogButtonTextStyle,
            )
        }

        Button(
            onClick = confirmAction,
            enabled = confirmEnabled,
            modifier = Modifier.weight(1f),
            shape = AppUISize.smallRoundedCornerShape,
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = AppUIColors.importantColor,
                    contentColor =
                        MaterialTheme.colorScheme.contentColorFor(
                            AppUIColors.importantColor,
                        ),
                ),
            contentPadding = PaddingValues(horizontal = medium, vertical = small2X),
        ) {
            Text(
                text = copywriter.getText(confirmTitle),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = dialogButtonTextStyle,
            )
        }
    }
}
