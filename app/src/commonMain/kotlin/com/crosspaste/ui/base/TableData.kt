package com.crosspaste.ui.base

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import com.crosspaste.ui.theme.AppUISize.zero

interface TableData {

    fun getData(): List<TableRow>

    @Composable
    fun measureColumnWidth(
        index: Int,
        style: TextStyle,
    ): Dp {
        var maxWidth by remember { mutableStateOf(zero) }
        for (tableRow in getData()) {
            maxWidth =
                maxOf(maxWidth, measureTextWidth(tableRow.getColumns()[index], style))
        }
        return maxWidth
    }
}

interface TableRow {

    fun getColumns(): List<String>
}

class TableRowImpl(
    private val columns: List<String>,
) : TableRow {
    override fun getColumns(): List<String> = columns
}
