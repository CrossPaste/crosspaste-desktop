package com.crosspaste.net.cli

import com.crosspaste.app.AppFileType
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.utils.getFileUtils
import io.github.oshai.kotlinlogging.KotlinLogging
import okio.Path

class CliTokenManager(
    private val userDataPathProvider: UserDataPathProvider,
) {

    private val logger = KotlinLogging.logger {}

    private val fileUtils = getFileUtils()

    private var currentToken: String = ""

    private val tokenPath: Path
        get() = userDataPathProvider.resolve("cli-token", AppFileType.USER)

    fun generateAndWriteToken() {
        val token = generateToken()
        currentToken = token
        writeTokenFile(token)
        logger.info { "CLI token generated and written to $tokenPath" }
    }

    fun validate(token: String): Boolean = token.isNotEmpty() && token == currentToken

    private fun generateToken(): String {
        val bytes = ByteArray(32)
        val random = java.security.SecureRandom()
        random.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun writeTokenFile(token: String) {
        val path = tokenPath
        fileUtils.fileSystem.write(path) {
            write(token.encodeToByteArray())
        }
        // Set file permissions to 0600 (owner read/write only)
        path.toFile().setReadable(false, false)
        path.toFile().setWritable(false, false)
        path.toFile().setReadable(true, true)
        path.toFile().setWritable(true, true)
    }
}
