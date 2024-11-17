package com.crosspaste.ui.base

import androidx.compose.material.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

@Composable
fun FileIcon(modifier: Modifier = Modifier) {
    Icon(
        modifier = modifier,
        painter = file(),
        contentDescription = "fileType",
        tint = Color(0xFFC5CACC),
    )
}

@Composable
fun FolderIcon(modifier: Modifier = Modifier) {
    Icon(
        modifier = modifier,
        painter = folder(),
        contentDescription = "folderType",
        tint = Color(0xFFF2AB4F),
    )
}

@Composable
fun FileSlashIcon(modifier: Modifier = Modifier) {
    Icon(
        modifier = modifier,
        painter = fileSlash(),
        contentDescription = "fileSlashType",
        tint = Color(0xFFC5CACC),
    )
}
