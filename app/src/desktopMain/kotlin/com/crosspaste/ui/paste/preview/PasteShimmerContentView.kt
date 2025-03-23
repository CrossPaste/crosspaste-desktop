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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.crosspaste.paste.PasteSingleProcess
import com.valentinilk.shimmer.shimmer

@Composable
fun PasteShimmerContentView(singleProcess: PasteSingleProcess?) {
    val process = singleProcess?.process?.collectAsState()

    Row(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color.Transparent)
                .shimmer(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(100.dp)
                    .padding(10.dp)
                    .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            process?.let {
                Text(
                    text = "${(it.value * 100).toInt()}%",
                    color = MaterialTheme.colorScheme.onSurface,
                    style =
                        TextStyle(
                            fontSize = 14.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                        ),
                )
            }
        }

        Column(
            modifier =
                Modifier.height(100.dp)
                    .width(290.dp)
                    .background(Color.Transparent)
                    .padding(10.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier =
                    Modifier
                        .height(26.dp)
                        .width(290.dp)
                        .padding(bottom = 5.dp)
                        .background(MaterialTheme.colorScheme.secondaryContainer),
            )
            Row(modifier = Modifier.height(26.dp).width(290.dp)) {
                Box(
                    modifier =
                        Modifier
                            .height(26.dp)
                            .width(160.dp)
                            .padding(vertical = 5.dp)
                            .background(MaterialTheme.colorScheme.secondaryContainer),
                )
                Spacer(modifier = Modifier.width(20.dp))
                Box(
                    modifier =
                        Modifier
                            .height(26.dp)
                            .width(119.dp)
                            .padding(vertical = 5.dp)
                            .background(MaterialTheme.colorScheme.secondaryContainer),
                )
            }
            Box(
                modifier =
                    Modifier
                        .height(26.dp)
                        .width(290.dp).padding(top = 6.dp)
                        .background(MaterialTheme.colorScheme.secondaryContainer),
            )
        }
    }
}
