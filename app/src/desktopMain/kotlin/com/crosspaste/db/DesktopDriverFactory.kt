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

                // Pragmas must go through connection properties (sqlite-jdbc's
                // SQLiteConfig): a multi-statement connectionInitSql silently
                // executes only its first statement, leaving synchronous=FULL
                // and busy_timeout at the driver default (3 s) — which surfaced
                // as SQLITE_BUSY under concurrent clipboard-burst writes.
                addDataSourceProperty("journal_mode", "WAL")
                addDataSourceProperty("synchronous", "NORMAL")
                addDataSourceProperty("busy_timeout", "10000")
                // Every transaction here writes. BEGIN IMMEDIATE takes the write lock
                // up front, so a read-then-write transaction waits on busy_timeout
                // instead of failing with SQLITE_BUSY_SNAPSHOT when another
                // connection commits between its read and its write.
                addDataSourceProperty("transaction_mode", "IMMEDIATE")
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
