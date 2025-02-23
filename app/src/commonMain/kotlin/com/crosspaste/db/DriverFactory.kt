package com.crosspaste.db

import app.cash.sqldelight.db.SqlDriver
import com.crosspaste.Database

interface DriverFactory {

    val dbName: String

    fun createDriver(): SqlDriver
}

fun createDatabase(driverFactory: DriverFactory): Database {
    val driver = driverFactory.createDriver()
    return Database(
        driver,
        PasteTaskEntity.Adapter(Int2LongAdapter, Int2LongAdapter),
    )
}
