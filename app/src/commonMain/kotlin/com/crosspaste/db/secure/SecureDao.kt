package com.crosspaste.db.secure

import com.crosspaste.Database
import com.crosspaste.utils.ioDispatcher
import kotlinx.coroutines.withContext

class SecureDao(private val database: Database): SecureIO {

    private val secureDatabaseQueries = database.secureDatabaseQueries

    override suspend fun saveCryptPublicKey(
        appInstanceId: String,
        serialized: ByteArray,
    ): Unit = withContext(ioDispatcher) {
        secureDatabaseQueries.saveCryptPublicKey(appInstanceId, serialized)
    }

    override suspend fun existCryptPublicKey(appInstanceId: String): Boolean = withContext(ioDispatcher) {
        secureDatabaseQueries.checkKeyExists(appInstanceId).executeAsOne()
    }

    override suspend fun deleteCryptPublicKey(appInstanceId: String): Unit = withContext(ioDispatcher) {
        secureDatabaseQueries.deleteCryptPublicKey(appInstanceId)
    }

    override suspend fun serializedPublicKey(appInstanceId: String): ByteArray? = withContext(ioDispatcher) {
        secureDatabaseQueries.getSerialized(appInstanceId).executeAsOneOrNull()
    }
}
