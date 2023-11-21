package com.clipevery.encrypt

import com.clipevery.AppInfo
import com.clipevery.macos.MacosKeychainHelper
import com.clipevery.path.getPathProvider
import com.clipevery.platform.currentPlatform
import com.clipevery.presist.DesktopOneFilePersist
import com.clipevery.windows.WindowDapiHelper
import io.github.oshai.kotlinlogging.KotlinLogging

val logger = KotlinLogging.logger {}

fun getSignalProtocolFactory(appInfo: AppInfo): SignalProtocolFactory {
    val currentPlatform = currentPlatform()
    return if (currentPlatform.isMacos()) {
        MacosSignalProtocolFactory(appInfo)
    } else if (currentPlatform.isWindows()) {
        WindowsSignalProtocolFactory()
    } else {
        throw IllegalStateException("Unknown platform: ${currentPlatform.name}")
    }
}

class MacosSignalProtocolFactory(private val appInfo: AppInfo): SignalProtocolFactory {

    private val filePersist = DesktopOneFilePersist(getPathProvider().resolveUser("signal.data"))

    override fun createSignalProtocol(): SignalProtocolWithState {
        val file = filePersist.path.toFile()
        var deleteOldSignalProtocol = false
        if (file.exists()) {
            logger.info { "Found signalProtocol encrypt file" }
            val bytes = file.readBytes()
            val password = MacosKeychainHelper.getPassword(appInfo.appName, appInfo.userName)

            password?.let {
                logger.info { "Found password in keychain by ${appInfo.appName} ${appInfo.userName}" }
                try {
                    val secretKey = stringToSecretKey(it)
                    val decryptData = decryptData(secretKey, bytes)
                    return SignalProtocolWithState(
                        readSignalProtocol(decryptData),
                        CreateSignalProtocolState.EXISTING
                    )
                } catch (e: Exception) {
                    logger.error(e) { "Failed to decrypt signalProtocol" }
                }
            }

            deleteOldSignalProtocol = true
            if (file.delete()) {
                logger.info { "Delete signalProtocol encrypt file" }
            }

        } else {
            logger.info { "No found signalProtocol encrypt file"  }
        }

        logger.info { "Creating new SignalProtocol" }
        val signalProtocol = DesktopSignalProtocol()
        val data = writeSignalProtocol(signalProtocol)
        val password = MacosKeychainHelper.getPassword(appInfo.appName, appInfo.userName)

        val secretKey = password?.let {
            logger.info { "Found password in keychain by ${appInfo.appName} ${appInfo.userName}" }
            stringToSecretKey(it)
        } ?: run {
            logger.info { "Generating new password in keychain by ${appInfo.appName} ${appInfo.userName}" }
            val secretKey = generateAESKey()
            MacosKeychainHelper.setPassword(appInfo.appName, appInfo.userName, secretKeyToString(secretKey))
            secretKey
        }

        val encryptData = encryptData(secretKey, data)
        filePersist.save(encryptData)
        return SignalProtocolWithState(signalProtocol,
            if (deleteOldSignalProtocol) CreateSignalProtocolState.DELETE_GENERATE
            else CreateSignalProtocolState.NEW_GENERATE)
    }
}


class WindowsSignalProtocolFactory : SignalProtocolFactory {

    private val filePersist = DesktopOneFilePersist(getPathProvider().resolveUser("signal.data"))

    override fun createSignalProtocol(): SignalProtocolWithState {
        val file = filePersist.path.toFile()
        var deleteOldSignalProtocol = false
        if (file.exists()) {
            logger.info { "Found signalProtocol encrypt file" }
            filePersist.read()?.let {
                try {
                    val decryptData = WindowDapiHelper.decryptData(it)
                    decryptData?.let { byteArray ->
                        return SignalProtocolWithState(
                            readSignalProtocol(byteArray),
                            CreateSignalProtocolState.EXISTING
                        )
                    }
                } catch (e: Exception) {
                    logger.error(e) { "Failed to decrypt signalProtocol" }
                }
            }
            deleteOldSignalProtocol = true
            if (file.delete()) {
                logger.info { "Delete signalProtocol encrypt file" }
            }
        } else {
            logger.info { "No found signalProtocol encrypt file"  }
        }

        logger.info { "Creating new SignalProtocol" }
        val signalProtocol = DesktopSignalProtocol()
        val data = writeSignalProtocol(signalProtocol)
        val encryptData = WindowDapiHelper.encryptData(data)
        filePersist.save(encryptData)
        return SignalProtocolWithState(signalProtocol,
            if (deleteOldSignalProtocol) CreateSignalProtocolState.DELETE_GENERATE
            else CreateSignalProtocolState.NEW_GENERATE)
    }
}