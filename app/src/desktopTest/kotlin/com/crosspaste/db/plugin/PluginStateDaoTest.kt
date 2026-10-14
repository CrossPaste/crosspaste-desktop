package com.crosspaste.db.plugin

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.crosspaste.Database
import com.crosspaste.db.Int2LongAdapter
import com.crosspaste.db.PasteTaskEntity
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class PluginStateDaoTest {

    private fun database(driver: JdbcSqliteDriver) =
        Database(driver, PasteTaskEntity.Adapter(Int2LongAdapter, Int2LongAdapter))

    @Test
    fun `migration 6 creates the plugin state table`() =
        runTest {
            val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
            Database.Schema.create(driver)
            driver.execute(null, "DROP TABLE PluginStateEntity", 0)

            Database.Schema.migrate(driver, 6, 7)

            val dao = PluginStateDao(database(driver))
            dao.upsert(PluginState("com.example.p", "1.0.0", true, PluginState.SOURCE_LOCAL, 1L))
            assertEquals(listOf(PluginState("com.example.p", "1.0.0", true, "local", 1L)), dao.getAll())
            driver.close()
        }

    @Test
    fun `upsert keeps the original source and install time`() =
        runTest {
            val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
            Database.Schema.create(driver)
            val dao = PluginStateDao(database(driver))

            dao.upsert(PluginState("com.example.p", "1.0.0", true, PluginState.SOURCE_LOCAL, 1L))
            dao.upsert(PluginState("com.example.p", "1.1.0", false, "market", 2L))

            assertEquals(listOf(PluginState("com.example.p", "1.1.0", false, "local", 1L)), dao.getAll())
            driver.close()
        }
}
