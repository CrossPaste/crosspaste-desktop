package com.crosspaste.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import com.crosspaste.app.AppWindowManager
import com.crosspaste.app.ExitMode
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.ui.theme.AppUIColors
import com.crosspaste.ui.theme.AppUISize
import com.crosspaste.ui.theme.AppUISize.medium
import com.crosspaste.ui.theme.AppUISize.small3X
import com.crosspaste.ui.theme.AppUISize.tiny3X
import com.crosspaste.ui.theme.AppUISize.tinyRoundedCornerShape
import org.koin.compose.koinInject

@Composable
fun MainMenuView() {
    val appWindowManager = koinInject<AppWindowManager>()

    val prevMenuList by remember {
        mutableStateOf(
            listOf(
                MainMenuItem("pasteboard", Pasteboard),
                MainMenuItem("devices", Devices),
                MainMenuItem("scan", QrCode),
                MainMenuItem("settings", Settings),
            ),
        )
    }

    val nextMenuList by remember {
        mutableStateOf(
            listOf(
                MainMenuItem("import", Import),
                MainMenuItem("export", Export),
                MainMenuItem("shortcut_keys", ShortcutKeys),
                MainMenuItem("recommend", Recommend),
                MainMenuItem("about", About),
            ),
        )
    }

    val screen by appWindowManager.screenContext.collectAsState()

    val selectedIndex by remember(screen.screenType) {
        mutableStateOf(
            (prevMenuList + nextMenuList).indexOfFirst { it.screenType == screen.screenType },
        )
    }

    val exitApplication = LocalExitApplication.current

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(vertical = medium),
    ) {
        prevMenuList.forEachIndexed { index, item ->
            MainMenuItemView(item.title, background(index == selectedIndex)) {
                appWindowManager.setScreen(ScreenContext(item.screenType))
            }
        }

        Spacer(Modifier.weight(1f))

        nextMenuList.forEachIndexed { index, item ->
            MainMenuItemView(item.title, background(index == selectedIndex - prevMenuList.size)) {
                appWindowManager.setScreen(ScreenContext(item.screenType))
            }
        }

        MainMenuItemView("quit", AppUIColors.generalBackground) {
            exitApplication(ExitMode.EXIT)
        }
    }
}

@Composable
private fun background(selected: Boolean): Color =
    if (selected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        AppUIColors.generalBackground
    }

@Composable
fun MainMenuItemView(
    title: String,
    background: Color,
    onClick: () -> Unit,
) {
    val copywriter = koinInject<GlobalCopywriter>()

    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(AppUISize.xxxLarge),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .padding(horizontal = small3X)
                    .clip(tinyRoundedCornerShape)
                    .background(background)
                    .clickable(onClick = onClick),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = tiny3X),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    modifier = Modifier.padding(start = small3X),
                    text = copywriter.getText(title),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.contentColorFor(background),
                    textAlign = TextAlign.Start,
                    style =
                        MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Light,
                            lineHeight = TextUnit.Unspecified,
                        ),
                )
            }
        }
    }
}

data class MainMenuItem(
    val title: String,
    val screenType: ScreenType,
)
