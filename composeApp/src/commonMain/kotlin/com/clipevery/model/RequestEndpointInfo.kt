package com.clipevery.model

import com.clipevery.encrypt.base64Encode
import kotlinx.serialization.Serializable
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream

@Serializable
data class RequestEndpointInfo(val deviceInfo: DeviceInfo,
                               val port: Int) {

    fun getBase64Encode(token: Int): String {
        val byteStream = ByteArrayOutputStream()
        val dataStream = DataOutputStream(byteStream)
        encodeDeviceInfo(dataStream)
        dataStream.writeInt(port)
        val byteArray = byteStream.toByteArray()
        val size = byteArray.size
        val offset = token % size
        val byteArrayRotate = byteArray.rotate(offset)
        val saltByteStream = ByteArrayOutputStream()
        val saltDataStream = DataOutputStream(saltByteStream)
        saltDataStream.write(byteArrayRotate)
        saltDataStream.writeInt(token)
        return base64Encode(saltByteStream.toByteArray())
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

    private fun ByteArray.rotate(offset: Int): ByteArray {
        val effectiveOffset = offset % size
        if (effectiveOffset == 0 || this.isEmpty()) {
            return this.copyOf() // 如果偏移量为0或数组为空，则直接返回原数组的副本
        }

        val result = ByteArray(this.size)
        for (i in this.indices) {
            val newPosition = (i + effectiveOffset + this.size) % this.size
            result[newPosition] = this[i]
        }
        return result
    }
}
