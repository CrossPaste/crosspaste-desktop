package com.crosspaste.db.secure

import com.crosspaste.Database

class SecureDao(private val database: Database): SecureIO {

    private val secureDatabaseQueries = database.secureDatabaseQueries

    override fun saveCryptPublicKey(
        appInstanceId: String,
        serialized: ByteArray
    ): Boolean {
        return database.transactionWithResult {
            val result = secureDatabaseQueries.checkKeyExists(appInstanceId).executeAsOne()
            secureDatabaseQueries.saveCryptPublicKey(appInstanceId, serialized)
            result
        }
    }

    override fun existCryptPublicKey(appInstanceId: String): Boolean {
        return secureDatabaseQueries.checkKeyExists(appInstanceId).executeAsOne()
    }

    override fun deleteCryptPublicKey(appInstanceId: String) {
        secureDatabaseQueries.deleteCryptPublicKey(appInstanceId)
    }

    override fun serializedPublicKey(appInstanceId: String): ByteArray? {
        return secureDatabaseQueries.getSerialized(appInstanceId).executeAsOneOrNull()
    }
}