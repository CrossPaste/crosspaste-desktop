package com.crosspaste.db

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.crosspaste.Database
import com.crosspaste.app.AppFileType
import com.crosspaste.path.UserDataPathProvider

class DesktopDriverFactory(
    private val userDataPathProvider: UserDataPathProvider
): DriverFactory {
    override val dbName: String = "crosspaste.db"

    override fun createDriver(): SqlDriver {
        val path = userDataPathProvider.resolve(dbName, appFileType = AppFileType.DATA)
        val driver: SqlDriver = JdbcSqliteDriver("jdbc:sqlite:$path")
        Database.Schema.create(driver)
        return driver
    }
}
