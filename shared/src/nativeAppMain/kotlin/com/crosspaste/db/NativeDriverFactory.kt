package com.crosspaste.db

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import co.touchlab.sqliter.DatabaseConfiguration
import com.crosspaste.Database

class NativeDriverFactory(
    private val dbPath: String,
) : DriverFactory {

    override val dbName: String = "crosspaste.db"

    override var sqlDriver: SqlDriver? = null

    override fun createDriver(): SqlDriver {
        val driver =
            NativeSqliteDriver(
                schema = Database.Schema,
                name = "$dbPath/$dbName",
                onConfiguration = { config ->
                    config.copy(
                        extendedConfig =
                            DatabaseConfiguration.Extended(
                                busyTimeout = 10000,
                            ),
                    )
                },
            )
        driver.execute(null, "PRAGMA journal_mode=WAL", 0)
        driver.execute(null, "PRAGMA synchronous=NORMAL", 0)
        sqlDriver = driver
        return driver
    }

    override fun closeDriver() {
        sqlDriver?.close()
        sqlDriver = null
    }
}
