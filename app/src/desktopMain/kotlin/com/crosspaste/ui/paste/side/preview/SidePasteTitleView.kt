package com.crosspaste.ui.paste.side.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.crosspaste.app.AppControl
import com.crosspaste.db.paste.PasteDao
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.image.DesktopIconColorExtractor
import com.crosspaste.ui.LocalDesktopAppSizeValueState
import com.crosspaste.ui.LocalSearchWindowInfoState
import com.crosspaste.ui.LocalThemeState
import com.crosspaste.ui.base.PasteTooltipIconView
import com.crosspaste.ui.base.SidePasteTypeIconView
import com.crosspaste.ui.base.darkSideBarColors
import com.crosspaste.ui.base.favorite
import com.crosspaste.ui.base.lightSideBarColors
import com.crosspaste.ui.base.noFavorite
import com.crosspaste.ui.paste.PasteDataScope
import com.crosspaste.ui.theme.AppUISize.medium
import com.crosspaste.ui.theme.AppUISize.tiny4X
import com.crosspaste.ui.theme.DesktopAppUIFont
import com.crosspaste.utils.ColorUtils.getBestTextColor
import com.crosspaste.utils.DateUtils
import com.crosspaste.utils.RelativeTime
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
fun PasteDataScope.SidePasteTitleView() {
    val appControl = koinInject<AppControl>()
    val copywriter = koinInject<GlobalCopywriter>()
    val desktopIconColorExtractor = koinInject<DesktopIconColorExtractor>()
    val pasteDao = koinInject<PasteDao>()

    val sideTitleHeight = LocalDesktopAppSizeValueState.current.sideTitleHeight
    val showWindow = LocalSearchWindowInfoState.current.show
    val isCurrentThemeDark = LocalThemeState.current.isCurrentThemeDark

    val scope = rememberCoroutineScope()

    val type by remember(pasteData.id) { mutableStateOf(pasteData.getType()) }
    var background by remember(type, isCurrentThemeDark) {
        mutableStateOf(
            if (isCurrentThemeDark) {
                darkSideBarColors.getColor(type)
            } else {
                lightSideBarColors.getColor(type)
            },
        )
    }

    var favorite by remember(pasteData.id) {
        mutableStateOf(pasteData.favorite)
    }

    val onBackground by remember(background) {
        mutableStateOf(background.getBestTextColor())
    }

    var relativeTime by remember(pasteData.id) {
        mutableStateOf<RelativeTime?>(null)
    }

    LaunchedEffect(isCurrentThemeDark, pasteData.source) {
        pasteData.source?.let {
            desktopIconColorExtractor.getBackgroundColor(it)?.let { color -> background = color }
        }
    }

    LaunchedEffect(pasteData.id, showWindow) {
        relativeTime =
            if (showWindow) {
                DateUtils.getRelativeTime(pasteData.createTime)
            } else {
                null
            }
    }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(sideTitleHeight)
                .background(background)
                .padding(start = medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxHeight()
                    .wrapContentWidth(),
            verticalArrangement = Arrangement.Center,
        ) {
            Row(
                modifier = Modifier.wrapContentSize(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start,
            ) {
                Text(
                    text = copywriter.getText(pasteData.getTypeName()),
                    style =
                        DesktopAppUIFont.sidePasteTitleTextStyle.copy(
                            color = onBackground,
                        ),
                )
                Spacer(Modifier.width(tiny4X))
                PasteTooltipIconView(
                    painter = if (favorite) favorite() else noFavorite(),
                    contentDescription = "Favorite",
                    tint = onBackground,
                    hover = background.copy(alpha = 0.3f),
                    text = copywriter.getText(if (favorite) "remove_from_favorites" else "favorite"),
                ) {
                    if (appControl.isFavoriteEnabled()) {
                        scope.launch {
                            pasteDao.setFavorite(pasteData.id, !favorite)
                            favorite = !favorite
                        }
                    }
                }
            }

            relativeTime?.let {
                Text(
                    text = copywriter.getText(it.unit, it.value?.toString() ?: ""),
                    style =
                        DesktopAppUIFont.sidePasteTimeTextStyle.copy(
                            color = onBackground,
                        ),
                )
            }
        }
        Spacer(Modifier.weight(1f))
        SidePasteTypeIconView(
            modifier = Modifier.fillMaxHeight().wrapContentWidth(),
            tint = onBackground,
        )
    }
}
