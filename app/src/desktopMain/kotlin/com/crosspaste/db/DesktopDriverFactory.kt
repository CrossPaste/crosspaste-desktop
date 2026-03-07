package com.crosspaste.db

import app.cash.sqldelight.TransacterImpl
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema
import com.crosspaste.Database
import com.crosspaste.app.AppFileType
import com.crosspaste.path.UserDataPathProvider
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource

class DesktopDriverFactory(
    private val userDataPathProvider: UserDataPathProvider,
) : DriverFactory {
    override val dbName: String = "crosspaste.db"

    override var sqlDriver: SqlDriver? = null

    private var dataSource: HikariDataSource? = null

    override fun createDriver(): SqlDriver {
        val path = userDataPathProvider.resolve(dbName, appFileType = AppFileType.DATA)

        val config =
            HikariConfig().apply {
                jdbcUrl = "jdbc:sqlite:$path"

                driverClassName = "org.sqlite.JDBC"
                maximumPoolSize = 10
                minimumIdle = 2
                isAutoCommit = true
                poolName = "CrossPastePool"

                connectionInitSql = "PRAGMA journal_mode=WAL; PRAGMA synchronous=NORMAL; PRAGMA busy_timeout=10000;"
            }

        val hikariDataSource = HikariDataSource(config)
        this.dataSource = hikariDataSource

        val driver = HikariSqliteDriver(hikariDataSource)
        migrateIfNeeded(driver, Database.Schema)
        sqlDriver = driver
        return driver
    }

    private fun migrateIfNeeded(
        driver: HikariSqliteDriver,
        schema: SqlSchema<QueryResult.Value<Unit>>,
    ) {
        val transacter = object : TransacterImpl(driver) {}

        transacter.transaction {
            val version = driver.getVersion()

            if (version == 0L) {
                schema.create(driver).value
            }

            if (version < schema.version) {
                schema.migrate(driver, version, schema.version).value
                driver.setVersion(schema.version)
            }
        }
    }

    override fun closeDriver() {
        sqlDriver?.close()
        dataSource?.close()
        sqlDriver = null
        dataSource = null
    }
}
