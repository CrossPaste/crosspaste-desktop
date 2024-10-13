package com.crosspaste.ui.base

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.crosspaste.realm.paste.PasteData
import com.crosspaste.ui.selectColor

@Composable
fun PasteTitleView(
    pasteData: PasteData,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val title by remember(pasteData.pasteState) {
        mutableStateOf(pasteData.getTitle())
    }

    Box(
        modifier = Modifier.fillMaxWidth().height(40.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier =
                Modifier.fillMaxWidth()
                    .fillMaxHeight()
                    .padding(horizontal = 10.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (selected) {
                            MaterialTheme.colorScheme.selectColor()
                        } else {
                            MaterialTheme.colorScheme.background
                        },
                    )
                    .clickable(onClick = onClick),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 5.dp),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PasteTypeIconView(pasteData)

                Text(
                    modifier = Modifier.padding(start = 10.dp),
                    text = title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onBackground,
                    style =
                        TextStyle(
                            textAlign = TextAlign.Start,
                            fontWeight = FontWeight.Light,
                            fontSize = 14.sp,
                            fontFamily = FontFamily.SansSerif,
                        ),
                )
            }
        }
    }
}
