package com.clipevery.model

import com.clipevery.encrypt.base64Encode
import org.signal.libsignal.protocol.IdentityKey
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream

data class RequestEndpointInfo(val deviceInfo: DeviceInfo,
                               val port: Int,
                               val publicKey: IdentityKey) {

    fun getBase64Encode(salt: Int): String {
        val byteStream = ByteArrayOutputStream()
        val dataStream = DataOutputStream(byteStream)
        dataStream.writeInt(salt)
        encodeDeviceInfo(dataStream)
        dataStream.writeInt(port)
        val serialize: ByteArray = publicKey.serialize()
        dataStream.writeInt(serialize.size)
        dataStream.write(serialize)
        return base64Encode(byteStream.toByteArray())
    }

    private fun encodeDeviceInfo(dataOutputStream: DataOutputStream) {
        dataOutputStream.writeUTF(deviceInfo.deviceId)
        dataOutputStream.writeUTF(deviceInfo.deviceName)
        encodePlatform(dataOutputStream)
        dataOutputStream.writeInt(deviceInfo.hostInfoList.size)
        deviceInfo.hostInfoList.forEach {
            dataOutputStream.writeUTF(it.displayName)
            dataOutputStream.writeUTF(it.hostAddress)
        }
    }

    private fun encodePlatform(dataOutputStream: DataOutputStream) {
        dataOutputStream.writeUTF(deviceInfo.platform.name)
        dataOutputStream.writeUTF(deviceInfo.platform.arch)
        dataOutputStream.writeInt(deviceInfo.platform.bitMode)
        dataOutputStream.writeUTF(deviceInfo.platform.version)
    }
}
