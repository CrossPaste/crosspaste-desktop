package com.clipevery.utils

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString

object TxtRecordUtils {

    inline fun <reified T: @Serializable Any> encodeToTxtRecordDict(obj: T, chunkSize: Int = 128): Map<String, String> {
        // 将对象序列化为 JSON 字符串
        val jsonString = JsonUtils.JSON.encodeToString(obj)

        // 将 JSON 字符串转换为 Base64 编码的字符串
        val base64Encoded = EncryptUtils.base64Encode(jsonString.toByteArray(Charsets.UTF_8))

        // 准备一个字典来存储分割后的数据
        val txtRecordDict = mutableMapOf<String, String>()

        // 分割 Base64 编码的字符串
        var index = 0
        base64Encoded.chunked(chunkSize).forEach { chunk ->
            txtRecordDict[index.toString()] = chunk
            index++
        }

        return txtRecordDict
    }

    inline fun <reified T: @Serializable Any> decodeFromTxtRecordDict(txtRecordDict: Map<String, ByteArray>): T {
        // 将分割后的数据组合成一个完整的 Base64 编码的字符串
        val base64Encoded = txtRecordDict.toSortedMap().values.joinToString(separator = "") { chunk ->
            String(chunk, Charsets.UTF_8)
        }

        // 将 Base64 字符串解码为 JSON 字符串
        val jsonString = String(EncryptUtils.base64Decode(base64Encoded), Charsets.UTF_8)

        // 从 JSON 字符串反序列化对象
        return JsonUtils.JSON.decodeFromString(jsonString)
    }
}