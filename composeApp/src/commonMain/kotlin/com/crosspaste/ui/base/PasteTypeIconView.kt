package com.crosspaste.ui.base

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.crosspaste.app.AppFileType
import com.crosspaste.image.FaviconLoader
import com.crosspaste.image.FileExtImageLoader
import com.crosspaste.image.ImageData
import com.crosspaste.image.ImageDataLoader
import com.crosspaste.paste.item.PasteFileCoordinate
import com.crosspaste.paste.item.PasteFiles
import com.crosspaste.paste.item.PasteUrl
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.realm.paste.PasteData
import com.crosspaste.realm.paste.PasteType
import okio.FileSystem
import org.koin.compose.koinInject

@Composable
fun PasteTypeIconView(
    pasteData: PasteData,
    padding: Dp = 2.dp,
    size: Dp = 20.dp,
) {
    val density = LocalDensity.current
    val iconStyle = koinInject<IconStyle>()
    val imageDataLoader = koinInject<ImageDataLoader>()
    val faviconLoader = koinInject<FaviconLoader>()
    val fileExtLoader = koinInject<FileExtImageLoader>()
    val userDataPathProvider = koinInject<UserDataPathProvider>()

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
                            val pasteFileCoordinate = PasteFileCoordinate(pasteData.getPasteCoordinate(), path)
                            return@AsyncView imageDataLoader.loadImageData(pasteFileCoordinate, density)
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
                        tint = MaterialTheme.colorScheme.primary,
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
                                val pasteFileCoordinate = PasteFileCoordinate(pasteData.getPasteCoordinate(), path)
                                return@AsyncView imageDataLoader.loadImageData(pasteFileCoordinate, density)
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
                        tint = MaterialTheme.colorScheme.primary,
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
            tint = MaterialTheme.colorScheme.primary,
        )
    } else {
        pasteData.source?.let {
            val path = userDataPathProvider.resolve("$it.png", AppFileType.ICON)
            if (FileSystem.SYSTEM.exists(path)) {
                val isMacStyleIcon by remember(it) { mutableStateOf(iconStyle.isMacStyleIcon(it)) }
                AppImageIcon(path, isMacStyleIcon = isMacStyleIcon, size = size + 2.dp)
            } else {
                Icon(
                    painter = loadIconData.readPainter(),
                    contentDescription = "Paste Icon",
                    modifier = Modifier.padding(padding).size(size),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        } ?: run {
            Icon(
                painter = loadIconData.readPainter(),
                contentDescription = "Paste Icon",
                modifier = Modifier.padding(padding).size(size),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
