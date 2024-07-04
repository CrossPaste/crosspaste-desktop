package com.crosspaste.ui.paste.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.crosspaste.LocalKoinApplication
import com.crosspaste.dao.paste.PasteData
import com.crosspaste.paste.PasteSingleProcess
import com.crosspaste.paste.PasteSyncProcessManager
import com.crosspaste.ui.base.PasteProgressbar
import com.valentinilk.shimmer.shimmer
import org.mongodb.kbson.ObjectId

@Composable
fun PrePreviewView(pasteData: PasteData) {
    val current = LocalKoinApplication.current
    val pasteSyncProcessManager = current.koin.get<PasteSyncProcessManager<ObjectId>>()

    val singleProcess: PasteSingleProcess? by remember(pasteData.id) { mutableStateOf(pasteSyncProcessManager.getProcess(pasteData.id)) }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(5.dp)),
    ) {
        singleProcess?.process?.let {
            PasteProgressbar(it)
        }

        PasteSpecificPreviewContentView(
            backgroundColor = Color.Transparent,
            pasteMainContent = {
                BoxWithConstraints(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .background(Color.Transparent)
                            .clip(RoundedCornerShape(5.dp)),
                ) {
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
                                    .background(Color.Gray),
                            contentAlignment = Alignment.Center,
                        ) {
                            singleProcess?.process?.let {
                                Text(
                                    text = "${(it * 100).toInt()}%",
                                    color = MaterialTheme.colors.onBackground,
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
                                        .background(Color.Gray),
                            )
                            Row(modifier = Modifier.height(26.dp).width(290.dp)) {
                                Box(
                                    modifier =
                                        Modifier
                                            .height(26.dp)
                                            .width(160.dp)
                                            .padding(vertical = 5.dp)
                                            .background(Color.Gray),
                                )
                                Spacer(modifier = Modifier.width(20.dp))
                                Box(
                                    modifier =
                                        Modifier
                                            .height(26.dp)
                                            .width(119.dp)
                                            .padding(vertical = 5.dp)
                                            .background(Color.Gray),
                                )
                            }
                            Box(
                                modifier =
                                    Modifier
                                        .height(26.dp)
                                        .width(290.dp).padding(top = 6.dp)
                                        .background(Color.Gray),
                            )
                        }
                    }
                }
            },
            pasteRightInfo = { toShow ->
                PasteMenuView(pasteData = pasteData, toShow = toShow)
            },
        )
    }
}
