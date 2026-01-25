package com.crosspaste.ui.base

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.crosspaste.utils.getFileUtils

@Composable
fun ImageFileSize(
    fileSize: Long,
    modifier: Modifier = Modifier,
) {
    if (fileSize <= 0) {
        return
    }

    val fileUtils = getFileUtils()
    val formattedSize = fileUtils.formatBytes(fileSize)

    ImageInfoLabel(
        text = formattedSize,
        modifier = modifier,
    )
}
