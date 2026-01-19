package com.crosspaste.utils

import dev.whyoleg.cryptography.operations.Hasher
import okio.Path
import okio.buffer
import okio.use
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

expect fun getCodecsUtils(): CodecsUtils

val HEX_DIGITS: CharArray = "0123456789abcdef".toCharArray()

val CROSS_PASTE_SEED = 13043025u

val CROSSPASTE_HASH = MurmurHash3(CROSS_PASTE_SEED)

interface CodecsUtils {

    val fileUtils: FileUtils

    val sha256: Hasher

    @OptIn(ExperimentalEncodingApi::class)
    fun base64Encode(bytes: ByteArray): String = Base64.encode(bytes)

    @OptIn(ExperimentalEncodingApi::class)
    fun base64Decode(string: String): ByteArray = Base64.decode(string)

    fun hash(bytes: ByteArray): String =
        if (bytes.isEmpty()) {
            ""
        } else {
            val (hash1, hash2) = CROSSPASTE_HASH.hash128x64(bytes)
            buildString(32) {
                appendHex(hash1)
                appendHex(hash2)
            }
        }

    fun hash(path: Path): String = fileUtils.getFileHash(path)

    fun hashByString(string: String): String = hash(string.encodeToByteArray())

    fun hashByArray(array: Array<String>): String

    @OptIn(ExperimentalStdlibApi::class)
    fun sha256(path: Path): String {
        val sha256Hasher = sha256.createHashFunction()
        val bufferSize = fileUtils.fileBufferSize
        val buffer = ByteArray(bufferSize)

        fileUtils.fileSystem.source(path).buffer().use { bufferedSource ->
            while (true) {
                val bytesRead = bufferedSource.read(buffer, 0, bufferSize)
                if (bytesRead == -1) break
                sha256Hasher.update(buffer, 0, bytesRead)
            }
        }
        val hash = sha256Hasher.hashToByteArray()
        return hash.toHexString()
    }
}
