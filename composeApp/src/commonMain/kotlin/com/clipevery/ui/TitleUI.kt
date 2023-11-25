package com.clipevery.ui

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clipevery.loadImageBitmap
import compose.icons.TablerIcons
import compose.icons.tablericons.Settings

@Preview
@Composable
fun TitleUI() {

    val customFontFamily = FontFamily(
        Font(resource = "font/BebasNeue.otf", FontWeight.Normal)
    )

    Box(
        modifier = Modifier.background(Color.Black)
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.6f),
                        Color.Transparent
                    ),
                    startY = 0.0f,
                    endY = 3.0f
                )
            ),
    ) {
        Row(modifier = Modifier.align(Alignment.CenterStart)
            .wrapContentWidth(),
            horizontalArrangement = Arrangement.Center) {
            Image(
                modifier = Modifier.padding(13.dp, 13.dp, 13.dp, 13.dp)
                    .align(Alignment.CenterVertically)
                    .clip(RoundedCornerShape(3.dp))
                    .size(36.dp),
                bitmap = loadImageBitmap("clipevery_icon.png"),
                contentDescription = "QR Code",
            )
            Column(Modifier.wrapContentWidth()
                .align(Alignment.CenterVertically)
                .offset(y = 2.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Text(modifier = Modifier.align(Alignment.Start),
                    text = "Compile Future",
                    color = Color.White,
                    style = TextStyle(fontWeight = FontWeight.Light),
                    fontSize = 11.sp,
                    )
                Text(modifier = Modifier.align(Alignment.Start),
                    text = "Clipevery",
                    color = Color.White,
                    fontSize = 25.sp,
                    style = TextStyle(fontWeight = FontWeight.Bold),
                    fontFamily = customFontFamily,
                    )
            }
            Column(Modifier.fillMaxWidth()
                .align(Alignment.CenterVertically)
                .offset(y = 2.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {


                IconButton(
                    onClick = { /* Do something */ },
                    modifier = Modifier.padding(13.dp)
                        .align(Alignment.End)
                        .size(36.dp) // Set the size of the button
                        .background(Color.Black, CircleShape) // Set the background to blue and shape to circle
                ) {
                    // Icon inside the IconButton
                    Icon(modifier = Modifier.padding(3.dp)
                        .size(30.dp),
                        imageVector = TablerIcons.Settings,
                        contentDescription = "Settings",
                        tint = Color.White)
                }
            }
        }

    }

}