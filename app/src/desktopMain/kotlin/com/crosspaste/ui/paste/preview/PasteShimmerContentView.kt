package com.crosspaste.ui.paste.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.crosspaste.paste.PasteSingleProcess
import com.crosspaste.ui.LocalDesktopAppSizeValueState
import com.crosspaste.ui.theme.AppUIColors
import com.crosspaste.ui.theme.AppUIFont.NumberTextStyle
import com.crosspaste.ui.theme.AppUISize.small3X
import com.crosspaste.ui.theme.AppUISize.tiny3X
import com.crosspaste.ui.theme.AppUISize.tiny4X
import com.crosspaste.ui.theme.AppUISize.xLarge
import com.valentinilk.shimmer.shimmer

@Composable
fun PasteShimmerContentView(singleProcess: PasteSingleProcess?) {
    val appSizeValue = LocalDesktopAppSizeValueState.current
    val process = singleProcess?.process?.collectAsState()

    Row(
        modifier =
            Modifier
                .fillMaxSize()
                .background(AppUIColors.pasteBackground)
                .shimmer(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(appSizeValue.mainPasteSize.height)
                    .padding(small3X)
                    .background(AppUIColors.pasteShimmerColor),
            contentAlignment = Alignment.Center,
        ) {
            process?.let {
                Text(
                    text = "${(it.value * 100).toInt()}%",
                    style = NumberTextStyle(),
                )
            }
        }

        val width = appSizeValue.mainPasteSize.width - appSizeValue.mainPasteSize.height - small3X

        Column(
            modifier =
                Modifier
                    .height(appSizeValue.mainPasteSize.height)
                    .width(width)
                    .background(Color.Transparent)
                    .padding(vertical = small3X)
                    .padding(end = small3X),
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier =
                    Modifier
                        .padding(bottom = tiny4X)
                        .height(xLarge)
                        .width(width)
                        .background(AppUIColors.pasteShimmerColor),
            )
            Row(
                modifier =
                    Modifier
                        .padding(vertical = tiny4X)
                        .height(xLarge)
                        .width(width),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier =
                        Modifier
                            .height(xLarge)
                            .width((width - tiny3X) * 2 / 3)
                            .background(AppUIColors.pasteShimmerColor),
                )
                Spacer(modifier = Modifier.width(tiny3X))
                Box(
                    modifier =
                        Modifier
                            .height(xLarge)
                            .width((width - tiny3X) / 3)
                            .background(AppUIColors.pasteShimmerColor),
                )
            }
            Box(
                modifier =
                    Modifier
                        .padding(top = tiny4X)
                        .height(xLarge)
                        .width(width)
                        .background(AppUIColors.pasteShimmerColor),
            )
        }
    }
}
