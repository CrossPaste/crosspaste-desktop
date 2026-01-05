package com.crosspaste.ui.base

interface TableData {

    fun getData(): List<TableRow>
}

interface TableRow {

    fun getColumns(): List<String>
}

class TableRowImpl(
    private val columns: List<String>,
) : TableRow {
    override fun getColumns(): List<String> = columns
}
