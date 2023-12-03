package com.clipevery.presist.data

import app.cash.sqldelight.db.SqlDriver
import com.clipevery.Database

expect class DriverFactory {
    fun createDriver(): SqlDriver
}

fun createDatabase(driverFactory: DriverFactory): Database {
    val driver = driverFactory.createDriver()

    // Do more work with the database (see below).
    return Database(driver);
}