package com.crosspaste.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults.buttonColors
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.ui.LocalThemeExtState
import com.crosspaste.ui.base.AnimatedSegmentedControl
import com.crosspaste.ui.base.IconData
import com.crosspaste.ui.base.SectionHeader
import com.crosspaste.ui.base.color
import com.crosspaste.ui.base.file
import com.crosspaste.ui.base.hashtag
import com.crosspaste.ui.base.html
import com.crosspaste.ui.base.image
import com.crosspaste.ui.base.link
import com.crosspaste.ui.base.measureTextWidth
import com.crosspaste.ui.base.rtf
import com.crosspaste.ui.base.text
import com.crosspaste.ui.theme.AppUISize.massive
import com.crosspaste.ui.theme.AppUISize.medium
import com.crosspaste.ui.theme.AppUISize.xLarge
import com.crosspaste.ui.theme.AppUISize.xxxLarge
import com.crosspaste.ui.theme.AppUISize.xxxxLarge
import com.crosspaste.utils.Quadruple
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
fun StorageStatisticsScope.StorageStatisticsHeader() {
    val copywriter = koinInject<GlobalCopywriter>()

    SectionHeader("storage_statistics") {
        val all = copywriter.getText("all")
        val favorite = copywriter.getText("favorite")
        val selectedItem =
            if (allOrFavorite) {
                all
            } else {
                favorite
            }

        AnimatedSegmentedControl(
            items = listOf(all, favorite),
            selectedItem = selectedItem,
            onItemSelected = {
                scope.launch {
                    allOrFavorite = it == all
                    refresh()
                }
            },
        )
    }
}

@Composable
fun StorageStatisticsScope.StorageStatisticsContentView() {
    val copywriter = koinInject<GlobalCopywriter>()
    val themeExt = LocalThemeExtState.current

    LaunchedEffect(Unit) {
        while (true) {
            refresh()
            delay(5000)
        }
    }

    var nameMaxWidth by remember { mutableStateOf(massive) }

    val pasteTypes: Array<Quadruple<String, Painter, Long?, String?>> =
        arrayOf(
            Quadruple("pasteboard", hashtag(), pasteCount, pasteFormatSize),
            Quadruple("text", text(), textCount, textFormatSize),
            Quadruple("color", color(), colorCount, colorFormatSize),
            Quadruple("link", link(), urlCount, urlFormatSize),
            Quadruple("html", html(), htmlCount, htmlFormatSize),
            Quadruple("rtf", rtf(), rtfCount, rtfFormatSize),
            Quadruple("image", image(), imageCount, imageFormatSize),
            Quadruple("file", file(), fileCount, fileFormatSize),
        )

    val typeTextStyle =
        MaterialTheme.typography.bodyMedium.copy(
            fontWeight = FontWeight.Medium,
        )

    for (property in pasteTypes) {
        nameMaxWidth =
            maxOf(
                nameMaxWidth,
                measureTextWidth(copywriter.getText(property.first), typeTextStyle),
            )
    }

    SettingSectionCard {
        Column {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(xxxxLarge)
                        .padding(horizontal = medium),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier =
                        Modifier
                            .widthIn(min = nameMaxWidth + xLarge + medium)
                            .wrapContentHeight(),
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        copywriter.getText("type"),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Medium,
                    )
                }

                Row(
                    modifier = Modifier.weight(0.2f),
                    horizontalArrangement = Arrangement.End,
                ) {
                    Text(
                        copywriter.getText("count"),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Medium,
                    )
                }

                Row(
                    modifier = Modifier.weight(0.3f),
                    horizontalArrangement = Arrangement.End,
                ) {
                    Text(
                        copywriter.getText("size"),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }

            HorizontalDivider()

            pasteTypes.forEachIndexed { index, quadruple ->
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(xxxxLarge)
                            .padding(horizontal = medium),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        modifier = Modifier.size(medium),
                        painter = quadruple.second,
                        contentDescription = null,
                    )

                    Spacer(modifier = Modifier.width(xLarge))

                    Text(
                        text = copywriter.getText(quadruple.first),
                        modifier = Modifier.width(nameMaxWidth),
                        style = typeTextStyle,
                        color = MaterialTheme.colorScheme.onSurface,
                    )

                    Row(
                        modifier = Modifier.weight(0.2f),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        if (quadruple.third != null) {
                            Text(
                                text = quadruple.third.toString(),
                                style =
                                    MaterialTheme.typography.bodyMedium.copy(
                                        fontFamily = FontFamily.Monospace,
                                    ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            CircularProgressIndicator(modifier = Modifier.size(xLarge))
                        }
                    }

                    Row(
                        modifier = Modifier.weight(0.3f),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        if (quadruple.fourth != null) {
                            Text(
                                text = quadruple.fourth,
                                style =
                                    MaterialTheme.typography.bodyMedium.copy(
                                        fontFamily = FontFamily.Monospace,
                                    ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            CircularProgressIndicator(modifier = Modifier.size(xLarge))
                        }
                    }
                }

                if (index != pasteTypes.size - 1) {
                    HorizontalDivider(modifier = Modifier.padding(start = xxxLarge))
                }
            }

            HorizontalDivider()

            SettingListItem(
                title = "clear_non_favorite_pasteboards",
                icon = IconData(Icons.Default.Delete, themeExt.redIconColor),
                trailingContent = {
                    if (!cleaning) {
                        Button(
                            onClick = {
                                cleaning = true
                                scope.launch {
                                    pasteDao
                                        .markAllDeleteExceptFavorite()
                                        .apply {
                                            cleaning = false
                                        }
                                }
                            },
                            colors =
                                buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error,
                                    contentColor = MaterialTheme.colorScheme.onError,
                                ),
                        ) {
                            Text(copywriter.getText("manual_clear"))
                        }
                    } else {
                        CircularProgressIndicator(
                            modifier = Modifier.size(medium),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                },
            )
        }
    }
}
