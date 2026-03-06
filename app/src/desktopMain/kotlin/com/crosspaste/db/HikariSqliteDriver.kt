package com.crosspaste.db

import app.cash.sqldelight.Query
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.driver.jdbc.JdbcDriver
import java.sql.Connection
import javax.sql.DataSource

class HikariSqliteDriver(
    private val dataSource: DataSource,
) : JdbcDriver() {

    private val listeners = linkedMapOf<String, MutableSet<Query.Listener>>()

    override fun addListener(vararg queryKeys: String, listener: Query.Listener) {
        synchronized(listeners) {
            queryKeys.forEach {
                listeners.getOrPut(it) { linkedSetOf() }.add(listener)
            }
        }
    }

    override fun removeListener(vararg queryKeys: String, listener: Query.Listener) {
        synchronized(listeners) {
            queryKeys.forEach {
                listeners[it]?.remove(listener)
            }
        }
    }

    override fun notifyListeners(vararg queryKeys: String) {
        val listenersToNotify = linkedSetOf<Query.Listener>()
        synchronized(listeners) {
            queryKeys.forEach { listeners[it]?.let(listenersToNotify::addAll) }
        }
        listenersToNotify.forEach(Query.Listener::queryResultsChanged)
    }

    override fun getConnection(): Connection {
        return dataSource.connection
    }

    override fun closeConnection(connection: Connection) {
        connection.close()
    }

    fun getVersion(): Long {
        val mapper = { cursor: app.cash.sqldelight.db.SqlCursor ->
            QueryResult.Value(if (cursor.next().value) cursor.getLong(0) else null)
        }
        return executeQuery(null, "PRAGMA user_version", mapper, 0, null).value ?: 0L
    }

    fun setVersion(version: Long) {
        execute(null, "PRAGMA user_version = $version", 0, null).value
    }
}