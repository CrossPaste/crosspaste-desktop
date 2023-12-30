package com.clipevery.dao

import com.clipevery.Database
import com.clipevery.sql.PreKey
import com.clipevery.sql.SignedPreKey
import org.signal.libsignal.protocol.ecc.Curve
import org.signal.libsignal.protocol.ecc.ECPrivateKey
import org.signal.libsignal.protocol.state.PreKeyRecord
import org.signal.libsignal.protocol.state.SignedPreKeyRecord
import org.signal.libsignal.protocol.util.Medium
import java.util.Random

class SignalStoreDaoImpl(override val database: Database): SignalStoreDao {

    @Synchronized
    override fun generatePreKeyPair(): PreKey {
        val preKeyPair = Curve.generateKeyPair()
        val random = Random()
        var preKeyId: Int
        do {
            preKeyId = random.nextInt(Medium.MAX_VALUE)
        } while (database.preKeyQueries.selectById(preKeyId.toLong()).executeAsOneOrNull() != null)
        val preKeyRecord = PreKeyRecord(preKeyId, preKeyPair)
        val serialize = preKeyRecord.serialize()
        database.preKeyQueries.insert(preKeyId.toLong(), serialize)
        return PreKey(preKeyId.toLong(), serialize)
    }

    @Synchronized
    override fun generatesSignedPreKeyPair(privateKey: ECPrivateKey): SignedPreKey {
        val random = Random()
        val signedPreKeyId = random.nextInt(Medium.MAX_VALUE)
        database.signedPreKeyQueries.selectById(signedPreKeyId.toLong()).executeAsOneOrNull()?.let { signedPrekey ->
            return signedPrekey
        } ?: run {
            val signedPreKeyPair = Curve.generateKeyPair()
            val signedPreKeySignature = Curve.calculateSignature(
                privateKey,
                signedPreKeyPair.publicKey.serialize()
            )
            val signedPreKeyRecord = SignedPreKeyRecord(
                signedPreKeyId, System.currentTimeMillis(), signedPreKeyPair, signedPreKeySignature
            )
            val serialize = signedPreKeyRecord.serialize()
            database.signedPreKeyQueries.insert(signedPreKeyId.toLong(), serialize)
            return SignedPreKey(signedPreKeyId.toLong(), serialize)
        }
    }
}
