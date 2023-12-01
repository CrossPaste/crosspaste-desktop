package com.clipevery.data

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.clipevery.Database
import com.clipevery.config.FileType
import com.clipevery.path.getPathProvider
import java.nio.file.Path

actual class DriverFactory {

    private val dbFilePath: Path = getPathProvider().resolve("clipevery.db", FileType.DATA)

    actual fun createDriver(): SqlDriver {
        val driver: SqlDriver = JdbcSqliteDriver("jdbc:sqlite:${dbFilePath.toAbsolutePath()}")
        Database.Schema.create(driver)
        return driver
    }
}