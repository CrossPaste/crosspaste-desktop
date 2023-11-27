package com.clipevery.ui

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.Icon
import androidx.compose.material.Tab
import androidx.compose.material.TabRow
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clipevery.LocalKoinApplication
import com.clipevery.config.ConfigManager
import com.clipevery.i18n.GlobalCopywriter

@Composable
fun TabsUI() {
    val current = LocalKoinApplication.current
    val copywriter = current.koin.get<GlobalCopywriter>()
    val configManager = current.koin.get<ConfigManager>()
    val config = remember { mutableStateOf(configManager.config) }
    var selectedTabIndex by remember { mutableStateOf(0) }

    val tabTitles = listOf("Clipboard", "Devices", "Scan")
    Row(modifier = Modifier.padding(8.dp)
        .wrapContentWidth()) {

        tabTitles.forEachIndexed { index, title ->
            Box(modifier = Modifier.padding(3.dp)
                .wrapContentSize(Alignment.BottomStart)
            )  {

                val style = if (index == selectedTabIndex) {
                    TextStyle(fontWeight = FontWeight.Bold)
                } else {
                    TextStyle(fontWeight = FontWeight.Normal)
                }


                Text(
                    text = copywriter.getText(title),
                    fontSize = 12.sp,
                    style = style,
                    modifier = Modifier
                        .padding(8.dp)
                        .align(Alignment.BottomStart)
                        .clickable { selectedTabIndex = index }
                )
            }
        }

        Spacer(modifier = Modifier.fillMaxWidth())
    }

    Column(modifier = Modifier.fillMaxSize()) {
        when (selectedTabIndex) {
            0 -> ClipPreview()
            1 -> Devices()
            2 -> {
                if (!config.value.bindingState) {
                    bindingQRCode()
                } else {
                    mainUI()
                }
            }
        }
    }
}


@Composable
@Preview
fun PreviewTabsUI() {
    var selectedTabIndex by remember { mutableStateOf(0) }
    var searchText by remember { mutableStateOf("") }
    val tabTitles = listOf("Tab 1", "Tab 2", "Tab 3")

    Row(modifier = Modifier.padding(8.dp).fillMaxWidth()) {
        Column(modifier = Modifier.padding(8.dp).wrapContentWidth().wrapContentHeight()) {
            TabRow(
                modifier = Modifier.wrapContentWidth().wrapContentHeight(),
                selectedTabIndex = selectedTabIndex,
                backgroundColor = Color.White
            ) {
                tabTitles.forEachIndexed { index, title ->
                    Tab(
                        modifier = Modifier.wrapContentWidth().wrapContentHeight(),
                        selected = index == selectedTabIndex,
                        onClick = { selectedTabIndex = index },
                        text = { Text(modifier = Modifier.wrapContentWidth(), text = title, fontSize = 12.sp) }
                    )
                }
            }
        }
        BasicTextField(
            value = searchText,
            onValueChange = { searchText = it },
            modifier = Modifier.width(100.dp), // Set a fixed width for the TextField
            decorationBox = { innerTextField ->
                Row {
                    Icon(Icons.Filled.Search, contentDescription = "Search Icon")
                    Box { innerTextField() }
                }
            }
        )
    }
    Column() {
        when (selectedTabIndex) {
            0 -> TabContent("Content for Tab 1")
            1 -> TabContent("Content for Tab 2")
            2 -> TabContent("Content for Tab 3")
        }
    }
}

@Composable
fun TabContent(text: String) {
    Text(text = text)
}

@Composable
fun mainUI() {
    Text("mainUI")
}