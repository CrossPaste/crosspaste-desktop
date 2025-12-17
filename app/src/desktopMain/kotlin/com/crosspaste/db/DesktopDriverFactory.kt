package com.crosspaste.db

import app.cash.sqldelight.db.SqlDriver
import com.crosspaste.Database
import com.crosspaste.app.AppFileType
import com.crosspaste.path.UserDataPathProvider
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource

class DesktopDriverFactory(
    private val userDataPathProvider: UserDataPathProvider
): DriverFactory {
    override val dbName: String = "crosspaste.db"

    override var sqlDriver: SqlDriver? = null

    private var dataSource: HikariDataSource? = null

    override fun createDriver(): SqlDriver {
        val path = userDataPathProvider.resolve(dbName, appFileType = AppFileType.DATA)

        val config = HikariConfig().apply {
            jdbcUrl = "jdbc:sqlite:$path"

            driverClassName = "org.sqlite.JDBC"
            maximumPoolSize = 10
            minimumIdle = 2
            isAutoCommit = true
            poolName = "CrossPastePool"

            addDataSourceProperty("busy_timeout", "10000")
            connectionInitSql = "PRAGMA journal_mode=WAL; PRAGMA synchronous=NORMAL;"
        }

        val hikariDataSource = HikariDataSource(config)
        this.dataSource = hikariDataSource

        val driver = HikariSqliteDriver(hikariDataSource)
        Database.Schema.create(driver)
        sqlDriver = driver
        return driver
    }

    override fun closeDriver() {
        sqlDriver?.close()
        dataSource?.close()
        sqlDriver = null
        dataSource = null
    }
}
