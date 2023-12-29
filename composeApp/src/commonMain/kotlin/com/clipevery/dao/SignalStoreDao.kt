package com.clipevery.dao

import com.clipevery.Database
import com.clipevery.sql.PreKey
import com.clipevery.sql.SignedPreKey
import org.signal.libsignal.protocol.ecc.ECPrivateKey

interface SignalStoreDao {

    val database: Database

    fun generatePreKeyPair(): PreKey

    fun generatesSignedPreKeyPair(privateKey: ECPrivateKey): SignedPreKey
}
