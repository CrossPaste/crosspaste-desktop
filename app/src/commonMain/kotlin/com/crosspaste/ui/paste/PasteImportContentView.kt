package com.crosspaste.ui.paste

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import com.crosspaste.app.AppFileChooser
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.paste.PasteImportParamFactory
import com.crosspaste.paste.PasteImportService
import com.crosspaste.ui.settings.SettingsText
import com.crosspaste.ui.theme.AppUIColors
import com.crosspaste.ui.theme.AppUISize.medium
import com.crosspaste.ui.theme.AppUISize.tiny
import com.crosspaste.ui.theme.AppUISize.tiny3X
import com.crosspaste.ui.theme.AppUISize.tiny4XRoundedCornerShape
import com.crosspaste.ui.theme.AppUISize.tinyRoundedCornerShape
import com.crosspaste.utils.ioDispatcher
import com.crosspaste.utils.mainDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject

@Composable
fun PasteImportContentView() {
    val appFileChooser = koinInject<AppFileChooser>()
    val copywriter = koinInject<GlobalCopywriter>()
    val pasteImportParamFactory = koinInject<PasteImportParamFactory>()

    val pasteImportService = koinInject<PasteImportService>()

    var progressing by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0f) }

    val coroutine = rememberCoroutineScope()

    Box(
        modifier =
            Modifier.fillMaxSize()
                .padding(medium)
                .clip(tinyRoundedCornerShape)
                .background(AppUIColors.generalBackground),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier =
                Modifier.fillMaxSize()
                    .padding(medium),
            verticalArrangement = Arrangement.spacedBy(tiny),
        ) {
            if (progressing) {
                LinearProgressIndicator(
                    modifier =
                        Modifier.fillMaxWidth()
                            .height(tiny3X)
                            .clip(tiny4XRoundedCornerShape),
                    progress = { progress },
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                SettingsText(
                    text = copywriter.getText("select_file_to_import"),
                )

                Button(
                    onClick = {
                        appFileChooser.openFileChooserToImport { path ->
                            val importParam = pasteImportParamFactory.createPasteImportParam(path)

                            coroutine.launch(ioDispatcher) {
                                withContext(mainDispatcher) {
                                    progress = 0f
                                    progressing = true
                                }
                                pasteImportService.import(importParam) {
                                    progress = it
                                    if (progress == 1f || progress < 0f) {
                                        progressing = false
                                        progress = 0f
                                    }
                                }
                            }
                        }
                    },
                ) {
                    Text(
                        if (progressing) {
                            "${(progress * 100).toInt()}%"
                        } else {
                            copywriter.getText("import")
                        },
                    )
                }
            }
        }
    }
}
