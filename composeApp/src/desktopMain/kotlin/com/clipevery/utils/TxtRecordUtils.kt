package com.clipevery.utils

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString

object TxtRecordUtils {

    // Encodes an object to a map suitable for TXT records, splitting the base64 encoded string into chunks.
    inline fun <reified T: @Serializable Any> encodeToTxtRecordDict(obj: T, chunkSize: Int = 128): Map<String, String> {
        // Serialize the object to a JSON string
        val jsonString = JsonUtils.JSON.encodeToString(obj)

        // Convert the JSON string to a base64 encoded string
        val base64Encoded = EncryptUtils.base64Encode(jsonString.toByteArray(Charsets.UTF_8))

        // Prepare a dictionary to store the split data
        val txtRecordDict = mutableMapOf<String, String>()

        // Split the base64 encoded string into chunks
        var index = 0
        base64Encoded.chunked(chunkSize).forEach { chunk ->
            txtRecordDict[index.toString()] = chunk
            index++
        }

        return txtRecordDict
    }

    // Decodes an object from a map of strings, reassembling the base64 encoded string from chunks.
    inline fun <reified T: @Serializable Any> decodeFromTxtRecordDict(txtRecordDict: Map<String, ByteArray>): T {
        // Combine the split data into a complete base64 encoded string
        val base64Encoded = txtRecordDict.toSortedMap().values.joinToString(separator = "") { chunk ->
            String(chunk, Charsets.UTF_8)
        }

        // Decode the base64 string into a JSON string
        val jsonString = String(EncryptUtils.base64Decode(base64Encoded), Charsets.UTF_8)

        // Deserialize the object from the JSON string
        return JsonUtils.JSON.decodeFromString(jsonString)
    }
}