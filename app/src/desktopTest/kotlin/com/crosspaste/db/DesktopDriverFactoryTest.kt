package com.crosspaste.db

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import com.crosspaste.app.AppFileType
import com.crosspaste.path.UserDataPathProvider
import io.mockk.every
import io.mockk.mockk
import okio.Path.Companion.toOkioPath
import java.nio.file.Files
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
}
