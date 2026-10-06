package com.crosspaste.db

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import com.crosspaste.app.AppFileType
import com.crosspaste.path.UserDataPathProvider
import io.mockk.every
import io.mockk.mockk
import okio.Path.Companion.toOkioPath
import java.nio.file.Files
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals

class DesktopDriverFactoryTest {

    private fun querySingle(
        driver: SqlDriver,
        sql: String,
    ): String? =
        driver
            .executeQuery(
                null,
                sql,
                { cursor ->
                    cursor.next()
                    QueryResult.Value(cursor.getString(0))
                },
                0,
                null,
            ).value

    // Guards against regressing to a multi-statement connectionInitSql: sqlite-jdbc
    // silently executes only the first statement of such a string, leaving
    // synchronous=FULL and busy_timeout at the driver default (3 s), which surfaces
    // as SQLITE_BUSY under concurrent writes.
    @Test
    fun `sqlite pragmas are applied on pooled connections`() {
        val tempDb =
            Files
                .createTempDirectory("crosspaste-driver-test")
                .resolve("crosspaste.db")
        val userDataPathProvider = mockk<UserDataPathProvider>()
        every {
            userDataPathProvider.resolve("crosspaste.db", AppFileType.DATA)
        } returns tempDb.toOkioPath()

        val factory = DesktopDriverFactory(userDataPathProvider)
        val driver = factory.createDriver()
        try {
            assertEquals("wal", querySingle(driver, "PRAGMA journal_mode"))
            // 1 = NORMAL
            assertEquals("1", querySingle(driver, "PRAGMA synchronous"))
            assertEquals("10000", querySingle(driver, "PRAGMA busy_timeout"))
        } finally {
            factory.closeDriver()
        }
    }

    // A read-then-write transaction must take the write lock up front. With SQLite's
    // default DEFERRED begin, a commit from another connection between the read and
    // the write fails the write with SQLITE_BUSY_SNAPSHOT, which busy_timeout does
    // not retry.
    @Test
    fun `read then write transaction survives a concurrent commit`() {
        val tempDb =
            Files
                .createTempDirectory("crosspaste-driver-test")
                .resolve("crosspaste.db")
        val userDataPathProvider = mockk<UserDataPathProvider>()
        every {
            userDataPathProvider.resolve("crosspaste.db", AppFileType.DATA)
        } returns tempDb.toOkioPath()

        val factory = DesktopDriverFactory(userDataPathProvider)
        val driver = factory.createDriver() as HikariSqliteDriver
        try {
            driver.execute(null, "CREATE TABLE t(v INTEGER)", 0, null)
            driver.execute(null, "INSERT INTO t VALUES (0)", 0, null)

            driver.getConnection().use { reader ->
                reader.autoCommit = false
                reader.createStatement().use { it.executeQuery("SELECT COUNT(*) FROM t").close() }

                val concurrentWrite =
                    thread {
                        driver.getConnection().use { writer ->
                            writer.createStatement().use { it.executeUpdate("INSERT INTO t VALUES (1)") }
                        }
                    }
                // DEFERRED: the other write commits here. IMMEDIATE: it waits on our lock.
                concurrentWrite.join(200)

                reader.createStatement().use { it.executeUpdate("INSERT INTO t VALUES (2)") }
                // Ends the transaction the way SQLDelight's JdbcDriver does; sqlite-jdbc
                // re-begins one on commit() until autoCommit is restored
                reader.commit()
                reader.autoCommit = true
                concurrentWrite.join()
            }

            assertEquals("3", querySingle(driver, "SELECT COUNT(*) FROM t"))
        } finally {
            factory.closeDriver()
        }
    }
}
