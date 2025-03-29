package com.crosspaste.ui.base

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.crosspaste.i18n.GlobalCopywriter
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
            Modifier.padding(top = 16.dp)
                .fillMaxWidth()
                .wrapContentHeight(),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        OutlinedButton(
            onClick = cancelAction,
            modifier = Modifier.weight(1f).padding(end = 8.dp),
        ) {
            Text(copywriter.getText(cancelTitle))
        }

        Button(
            onClick = confirmAction,
            modifier = Modifier.weight(1f).padding(start = 8.dp),
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                ),
        ) {
            Text(copywriter.getText(confirmTitle))
        }
    }
}
