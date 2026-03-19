package com.crosspaste.utils

import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.SHA256
import okio.Path
import okio.buffer
import okio.use

private val fileUtils by lazy { getFileUtils() }

private val sha256Hasher by lazy { CryptographyProvider.Default.get(SHA256).hasher() }

fun CodecsUtils.hash(path: Path): String = fileUtils.getFileHash(path)

@OptIn(ExperimentalStdlibApi::class)
fun CodecsUtils.sha256(path: Path): String {
    val sha256HashFunction = sha256Hasher.createHashFunction()
    val bufferSize = fileUtils.fileBufferSize
    val buffer = ByteArray(bufferSize)

    fileUtils.fileSystem.source(path).buffer().use { bufferedSource ->
        while (true) {
            val bytesRead = bufferedSource.read(buffer, 0, bufferSize)
            if (bytesRead == -1) break
            sha256HashFunction.update(buffer, 0, bytesRead)
        }
    }
    val hash = sha256HashFunction.hashToByteArray()
    return hash.toHexString()
}
