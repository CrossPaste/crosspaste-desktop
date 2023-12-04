package com.clipevery.dao

import com.clipevery.Database
import com.clipevery.model.AppInfo
import io.github.oshai.kotlinlogging.KotlinLogging
import org.signal.libsignal.protocol.IdentityKey
import org.signal.libsignal.protocol.IdentityKeyPair
import org.signal.libsignal.protocol.IdentityKeyPair.generate
import org.signal.libsignal.protocol.SignalProtocolAddress
import org.signal.libsignal.protocol.state.IdentityKeyStore
import org.signal.libsignal.protocol.util.KeyHelper

class DesktopIdentityKeyStore(private val database: Database,
                              private val appInfo: AppInfo): IdentityKeyStore {


    private val logger = KotlinLogging.logger {}

    private val identityKeyPair: IdentityKeyPair

    private val registrationId: Int

    init {
        val identityKeyPair = generate()
        val registrationId = KeyHelper.generateRegistrationId(false)
        try {
            database.identityKeyQueries.tryInit(appInfo.appInstanceId, registrationId.toLong(), identityKeyPair.serialize())
            logger.info { "init identityKey success, appInstanceId = ${appInfo.appInstanceId}" }
        } catch (ignore: Throwable) {
            logger.info { "identityKey exist, appInstanceId = ${appInfo.appInstanceId}" }
        }
        val identityKey = database.identityKeyQueries.select(appInfo.appInstanceId).executeAsOneOrNull()
        if (identityKey == null) {
            logger.error { "Failed to get identityKey, appInstanceId = ${appInfo.appInstanceId}" }
            this.identityKeyPair = identityKeyPair
            this.registrationId = registrationId
        } else {
            logger.info { "get identityKey success, appInstanceId = ${appInfo.appInstanceId}" }
            this.identityKeyPair = IdentityKeyPair(identityKey.serialized)
            this.registrationId = identityKey.registrationId.toInt()
        }
    }

    override fun getIdentityKeyPair(): IdentityKeyPair {
        return identityKeyPair
    }

    override fun getLocalRegistrationId(): Int {
        return registrationId
    }

    override fun saveIdentity(address: SignalProtocolAddress, identityKey: IdentityKey): Boolean {
        return try {
            database.identityKeyQueries.update(identityKey.serialize(), appInfo.appInstanceId, registrationId.toLong())
            true
        } catch (e: Exception) {
            false
        }
    }

    override fun isTrustedIdentity(
        address: SignalProtocolAddress,
        identityKey: IdentityKey,
        direction: IdentityKeyStore.Direction
    ): Boolean {
        val identity: IdentityKey? = getIdentity(address)
        return identity?.let { it == identityKey } ?: true
    }

    override fun getIdentity(address: SignalProtocolAddress): IdentityKey? {
        return database.syncQueries.selectPublicKey(address.name)
            .executeAsOneOrNull()?.let { selectPublicKey ->
                return selectPublicKey.public_key?.let {
                    return IdentityKey(it)
                }
            }
    }

}