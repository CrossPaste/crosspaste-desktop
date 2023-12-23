package com.clipevery.net

import com.clipevery.app.AppInfo
import com.clipevery.app.logger
import com.clipevery.endpoint.EndpointInfo
import com.clipevery.endpoint.EndpointInfoFactory
import com.clipevery.dto.model.ResponseSyncInfo
import com.clipevery.signal.ClipIdentityKeyStore
import com.clipevery.utils.base64Encode
import com.clipevery.utils.encodePreKeyBundle
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.signal.libsignal.protocol.state.PreKeyBundle
import org.signal.libsignal.protocol.state.PreKeyStore
import org.signal.libsignal.protocol.state.SignedPreKeyStore
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.net.InetAddress
import javax.jmdns.JmDNS
import javax.jmdns.ServiceInfo


class DesktopClipBonjourService(private val appInfo: AppInfo,
                                private val endpointInfoFactory: EndpointInfoFactory,
                                private val identityKeyStore: ClipIdentityKeyStore,
                                private val preKeyStore: PreKeyStore,
                                private val signedPreKeyStore: SignedPreKeyStore): ClipBonjourService {

    private val jmdnsMap: MutableMap<String, JmDNS> = mutableMapOf()

    override fun registerService(): ClipBonjourService {
        val endpointInfo = endpointInfoFactory.createEndpointInfo()
        val serviceInfoBytes = buildServiceInfo(endpointInfo)

        for (hostInfo in endpointInfo.hostInfoList) {
            val jmDNS: JmDNS = JmDNS.create(InetAddress.getByName(hostInfo.hostAddress))
            jmdnsMap.putIfAbsent(hostInfo.hostAddress, jmDNS)
            val serviceInfo = ServiceInfo.create(
                "_clipeveryService._tcp.local.",
                "clipevery@${appInfo.appInstanceId}@${hostInfo.hostAddress.replace(".", "_")}",
                endpointInfo.port,
                0,
                0,
                serviceInfoBytes
            )
            jmDNS.registerService(serviceInfo)
        }
        return this
    }

    override fun unregisterService(): ClipBonjourService {
        val iterator = jmdnsMap.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            val jmDNS = entry.value
            jmDNS.unregisterAllServices()
            jmDNS.close()
            iterator.remove()
        }
        return this
    }

    private fun buildServiceInfo(endpointInfo: EndpointInfo): ByteArray {
        val responseSyncInfo = ResponseSyncInfo(appInfo, endpointInfo)
        val responseSyncInfoJson = Json.encodeToString(responseSyncInfo)
        val responseSyncInfoJsonBytes = responseSyncInfoJson.encodeToByteArray()
        logger.debug { "Registering service: $responseSyncInfoJson" }

        val registrationId = identityKeyStore.localRegistrationId
        val deviceId = 1
        val preKeyId = identityKeyStore.getPreKeyId()
        val signedPreKeyId = identityKeyStore.getSignedPreKeyId()
        val preKeyPairPublicKey = preKeyStore.loadPreKey(preKeyId).keyPair.publicKey
        val signedPreKey = signedPreKeyStore.loadSignedPreKey(signedPreKeyId)

        val preKeyBundle = PreKeyBundle(
            registrationId,
            deviceId,
            preKeyId,
            preKeyPairPublicKey,
            signedPreKeyId,
            signedPreKey.keyPair.publicKey,
            signedPreKey.signature,
            identityKeyStore.identityKeyPair.publicKey
        )

        val encodePreKeyBundle = encodePreKeyBundle(preKeyBundle)

        val byteStream = ByteArrayOutputStream()
        val dataStream = DataOutputStream(byteStream)
        dataStream.writeInt(responseSyncInfoJsonBytes.size)
        dataStream.write(responseSyncInfoJsonBytes)
        dataStream.writeInt(encodePreKeyBundle.size)
        dataStream.write(encodePreKeyBundle)
        return base64Encode(byteStream.toByteArray()).toByteArray()
    }
}
