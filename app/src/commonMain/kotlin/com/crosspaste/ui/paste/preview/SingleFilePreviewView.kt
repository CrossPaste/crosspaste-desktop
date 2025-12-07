package com.crosspaste.ui.paste.preview

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.info.PasteInfos.MISSING_FILE
import com.crosspaste.paste.item.PasteFileInfoTreeCoordinate
import com.crosspaste.ui.LocalAppSizeValueState
import com.crosspaste.ui.base.SingleFileIcon
import com.crosspaste.ui.base.UISupport
import com.crosspaste.ui.theme.AppUIFont.propertyTextStyle
import com.crosspaste.ui.theme.AppUISize.tiny
import com.crosspaste.ui.theme.AppUISize.tiny2XRoundedCornerShape
import com.crosspaste.ui.theme.AppUISize.tiny3X
import com.crosspaste.utils.getFileUtils
import org.koin.compose.koinInject

@Composable
fun SingleFilePreviewView(
    pasteFileInfoTreeCoordinate: PasteFileInfoTreeCoordinate,
    width: Dp,
) {
    val copywriter = koinInject<GlobalCopywriter>()
    val uiSupport = koinInject<UISupport>()

    val appSizeValue = LocalAppSizeValueState.current

    val fileUtils = getFileUtils()

    val filePath = pasteFileInfoTreeCoordinate.filePath

    val existFile by remember(filePath) {
        mutableStateOf(fileUtils.existFile(filePath))
    }

    Row(
        modifier =
            Modifier
                .width(width)
                .wrapContentHeight()
                .clip(tiny2XRoundedCornerShape)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = {
                            uiSupport.browseFile(filePath)
                        },
                    )
                },
    ) {
        Box(modifier = Modifier.size(appSizeValue.mainPasteSize.height)) {
            SingleFileIcon(
                filePath = filePath,
                size = appSizeValue.mainPasteSize.height,
            )
        }

        Column(
            modifier =
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth()
                    .padding(horizontal = tiny)
                    .padding(bottom = tiny),
            verticalArrangement = Arrangement.Bottom,
        ) {
            Text(
                text = filePath.name,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface,
                style = propertyTextStyle,
            )

            Spacer(modifier = Modifier.height(tiny3X))

            if (existFile) {
                val fileSize =
                    remember(filePath) {
                        fileUtils.formatBytes(fileUtils.getFileSize(filePath))
                    }
                Text(
                    text = fileSize,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = propertyTextStyle,
                )
            } else {
                Text(
                    text = copywriter.getText(MISSING_FILE),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.error,
                    style = propertyTextStyle,
                )
            }
        }
    }
}
