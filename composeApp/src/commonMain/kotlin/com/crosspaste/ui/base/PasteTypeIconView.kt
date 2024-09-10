package com.crosspaste.ui.base

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.crosspaste.LocalKoinApplication
import com.crosspaste.app.AppFileType
import com.crosspaste.image.FaviconLoader
import com.crosspaste.image.FileExtImageLoader
import com.crosspaste.image.ImageData
import com.crosspaste.image.getImageDataLoader
import com.crosspaste.paste.item.PasteFiles
import com.crosspaste.paste.item.PasteUrl
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.realm.paste.PasteData
import com.crosspaste.realm.paste.PasteType
import okio.FileSystem

@Composable
fun PasteTypeIconView(
    pasteData: PasteData,
    padding: Dp = 2.dp,
    size: Dp = 20.dp,
) {
    val current = LocalKoinApplication.current
    val density = LocalDensity.current
    val iconStyle = current.koin.get<IconStyle>()
    val faviconLoader = current.koin.get<FaviconLoader>()
    val fileExtLoader = current.koin.get<FileExtImageLoader>()
    val userDataPathProvider = current.koin.get<UserDataPathProvider>()

    val imageDataLoader = getImageDataLoader()

    val loadIconData = imageDataLoader.loadPasteType(pasteData.pasteType)

    if (pasteData.pasteType == PasteType.URL) {
        AsyncView(
            key = pasteData.id,
            defaultValue = loadIconData,
            load = {
                pasteData.getPasteItem()?.let {
                    it as PasteUrl
                    try {
                        faviconLoader.load(it.url)?.let { path ->
                            return@AsyncView imageDataLoader.loadImageData(path, density)
                        }
                    } catch (ignore: Exception) {
                    }
                }
                loadIconData
            },
        ) { loadData ->
            if (loadData.isSuccess() && loadData is ImageData<*>) {
                if (loadData.isIcon) {
                    Icon(
                        painter = loadData.readPainter(),
                        contentDescription = "Paste Icon",
                        modifier = Modifier.padding(padding).size(size),
                        tint = MaterialTheme.colors.primary,
                    )
                } else {
                    Image(
                        painter = loadData.readPainter(),
                        contentDescription = "Paste Icon",
                        modifier = Modifier.padding(padding).size(size),
                    )
                }
            }
        }
    } else if (pasteData.pasteType == PasteType.FILE) {
        AsyncView(
            key = pasteData.id,
            defaultValue = loadIconData,
            load = {
                pasteData.getPasteItem()?.let {
                    it as PasteFiles
                    try {
                        val files = it.getPasteFiles(userDataPathProvider)
                        if (files.isNotEmpty()) {
                            fileExtLoader.load(files[0].getFilePath())?.let { path ->
                                return@AsyncView imageDataLoader.loadImageData(path, density)
                            }
                        }
                    } catch (ignore: Exception) {
                    }
                }
                loadIconData
            },
        ) { loadData ->
            if (loadData.isSuccess() && loadData is ImageData<*>) {
                if (loadData.isIcon) {
                    Icon(
                        painter = loadData.readPainter(),
                        contentDescription = "Paste Icon",
                        modifier = Modifier.padding(padding).size(size),
                        tint = MaterialTheme.colors.primary,
                    )
                } else {
                    Image(
                        painter = loadData.readPainter(),
                        contentDescription = "Paste Icon",
                        modifier = Modifier.padding(padding).size(size),
                    )
                }
            }
        }
    } else if (pasteData.pasteType != PasteType.HTML) {
        Icon(
            painter = loadIconData.readPainter(),
            contentDescription = "Paste Icon",
            modifier = Modifier.padding(padding).size(size),
            tint = MaterialTheme.colors.primary,
        )
    } else {
        pasteData.source?.let {
            val path = userDataPathProvider.resolve("$it.png", AppFileType.ICON)
            if (FileSystem.SYSTEM.exists(path)) {
                val isMacStyleIcon by remember(it) { mutableStateOf(iconStyle.isMacStyleIcon(it)) }
                AppImageIcon(path = path, isMacStyleIcon = isMacStyleIcon, size = size + 2.dp)
            } else {
                Icon(
                    painter = loadIconData.readPainter(),
                    contentDescription = "Paste Icon",
                    modifier = Modifier.padding(padding).size(size),
                    tint = MaterialTheme.colors.primary,
                )
            }
        } ?: run {
            Icon(
                painter = loadIconData.readPainter(),
                contentDescription = "Paste Icon",
                modifier = Modifier.padding(padding).size(size),
                tint = MaterialTheme.colors.primary,
            )
        }
    }
}
