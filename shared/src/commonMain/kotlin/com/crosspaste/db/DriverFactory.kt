package com.crosspaste.db

import app.cash.sqldelight.db.SqlDriver
import com.crosspaste.Database

interface DriverFactory {

    val dbName: String

    var sqlDriver: SqlDriver?

    fun createDriver(): SqlDriver

    fun closeDriver()
}

fun createDatabase(driverFactory: DriverFactory): Database {
    val driver = driverFactory.createDriver()
    return Database(
        driver,
        PasteTaskEntity.Adapter(Int2LongAdapter, Int2LongAdapter),
    )
}
