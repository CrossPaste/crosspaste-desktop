package com.crosspaste.db

import app.cash.sqldelight.ColumnAdapter

object Int2LongAdapter : ColumnAdapter<Int, Long> {
    override fun decode(databaseValue: Long): Int {
        return databaseValue.toInt()
    }

    override fun encode(value: Int): Long {
        return value.toLong()
    }
}
