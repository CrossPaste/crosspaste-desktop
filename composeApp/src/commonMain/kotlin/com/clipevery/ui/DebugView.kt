package com.clipevery.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

@Composable
fun DebugView() {
    PickerDemo()
}


enum class CleanTime(val quantity: Int, val unit: String) {
    // 示例数据
    ONE_HOUR(1, "Hour"),
    ONE_DAY(1, "Day"),
    ONE_WEEK(1, "Week");

    companion object {
        fun allCases() = values()
    }
}

@Composable
fun PickerDemo() {
    // 假设这个函数会返回不同语言环境下的文本
    fun copywriterGetText(key: String): String = key

    // 状态变量，用于存储选定的清理时间索引
    var selectedImageCleanTimeIndex by remember { mutableStateOf(0) }
    val cleanTimeOptions = CleanTime.allCases()

    MaterialTheme {
        Column {
            Text(copywriterGetText("Image expiration time"))
            // ComboBox
            OutlinedTextField(
                value = "${cleanTimeOptions[selectedImageCleanTimeIndex].quantity} ${copywriterGetText(cleanTimeOptions[selectedImageCleanTimeIndex].unit)}",
                onValueChange = {},
                readOnly = true // 让TextField不可编辑
            )

            DropdownMenu(
                expanded = true,
                onDismissRequest = { /* TODO */ }
            ) {
                cleanTimeOptions.forEachIndexed { index, cleanTime ->
                    DropdownMenuItem(
                        onClick = {
                            selectedImageCleanTimeIndex = index
                            // 用新值更新配置
                            // configManager.updateConfig(key: "imageCleanTimeIndex", value: selectedImageCleanTimeIndex)
                        }
                    ) {
                        Text("${cleanTime.quantity} ${copywriterGetText(cleanTime.unit)}")
                    }
                }
            }
        }
    }
}