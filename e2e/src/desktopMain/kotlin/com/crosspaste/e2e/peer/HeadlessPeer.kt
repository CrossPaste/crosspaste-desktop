package com.crosspaste.e2e.peer

import com.crosspaste.app.AppInfo
import com.crosspaste.config.CommonConfigManager
import com.crosspaste.net.PasteClient
import com.crosspaste.net.SyncApi
import com.crosspaste.net.clientapi.PasteClientApi
import com.crosspaste.net.clientapi.PullClientApi
import com.crosspaste.net.clientapi.SyncClientApi
import com.crosspaste.net.exception.DesktopExceptionHandler
import com.crosspaste.net.exception.ExceptionHandler
import com.crosspaste.net.plugin.ClientDecryptPlugin
import com.crosspaste.net.plugin.ClientEncryptPlugin
import com.crosspaste.secure.GeneralSecureStore
import com.crosspaste.secure.SecureKeyPair
import com.crosspaste.secure.SecureKeyPairSerializer
import com.crosspaste.secure.SecureStore
import com.crosspaste.utils.CryptographyUtils.generateSecureKeyPair
import com.crosspaste.utils.getJsonUtils
import kotlinx.coroutines.runBlocking
import java.util.UUID

class HeadlessPeer(
    appInstanceId: String = UUID.randomUUID().toString(),
    userName: String = "e2e-peer",
    appVersion: String = "0.0.0-e2e",
    appRevision: String = "Unknown",
) {
    // Force JsonUtils to fully initialize before anything else touches PasteItem
    // serializer registration. See MEMORY.md "PasteItem / JsonUtils Circular Class
    // Initialization".
    @Suppress("unused")
    private val jsonUtils = getJsonUtils()

    val appInfo: AppInfo =
        AppInfo(
            appInstanceId = appInstanceId,
            appVersion = appVersion,
            appRevision = appRevision,
            userName = userName,
        )

    val secureKeyPairSerializer: SecureKeyPairSerializer = SecureKeyPairSerializer()

    val secureKeyPair: SecureKeyPair = runBlocking { generateSecureKeyPair() }

    val secureIO: InMemorySecureIO = InMemorySecureIO()

    val secureStore: SecureStore =
        GeneralSecureStore(secureKeyPair, secureKeyPairSerializer, secureIO)

    val exceptionHandler: ExceptionHandler = DesktopExceptionHandler()

    val syncApi: SyncApi = SyncApi

    private val clientEncryptPlugin = ClientEncryptPlugin(secureStore)
    private val clientDecryptPlugin = ClientDecryptPlugin(secureStore)

    val pasteClient: PasteClient = PasteClient(appInfo, clientEncryptPlugin, clientDecryptPlugin)

    val configManager: CommonConfigManager = E2eConfigManager()

    val syncClientApi: SyncClientApi =
        SyncClientApi(
            pasteClient,
            exceptionHandler,
            secureKeyPairSerializer,
            secureStore,
            syncApi,
        )

    val pasteClientApi: PasteClientApi = PasteClientApi(pasteClient, configManager)

    val pullClientApi: PullClientApi = PullClientApi(pasteClient, configManager)

    val bonjourAdvertiser: BonjourAdvertiser = BonjourAdvertiser(appInfo).also { it.start() }

    fun close() {
        bonjourAdvertiser.close()
        pasteClient.close()
    }
}
