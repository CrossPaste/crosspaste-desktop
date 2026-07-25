package com.crosspaste.e2e.peer

import com.crosspaste.app.AppInfo
import com.crosspaste.config.CommonConfigManager
import com.crosspaste.net.PasteClient
import com.crosspaste.net.SyncApi
import com.crosspaste.net.clientapi.PairingV3ClientApi
import com.crosspaste.net.clientapi.PasteClientApi
import com.crosspaste.net.clientapi.PullClientApi
import com.crosspaste.net.clientapi.SyncClientApi
import com.crosspaste.net.exception.DesktopExceptionHandler
import com.crosspaste.net.exception.ExceptionHandler
import com.crosspaste.net.plugin.ClientDecryptPlugin
import com.crosspaste.net.plugin.ClientEncryptPlugin
import com.crosspaste.pairing.v3.BouncyCastlePakeEcOps
import com.crosspaste.pairing.v3.PairingAcceptanceWindow
import com.crosspaste.pairing.v3.PairingProtocolV3Service
import com.crosspaste.pairing.v3.PairingRateLimiter
import com.crosspaste.pairing.v3.PairingReceiptCache
import com.crosspaste.pairing.v3.PairingSessionStore
import com.crosspaste.pairing.v3.PairingV3
import com.crosspaste.pairing.v3.Spake2PakeProvider
import com.crosspaste.secure.GeneralSecureStore
import com.crosspaste.secure.SecureKeyPair
import com.crosspaste.secure.SecureKeyPairSerializer
import com.crosspaste.secure.SecureStore
import com.crosspaste.utils.CryptographyUtils.generateSecureKeyPair
import com.crosspaste.utils.getJsonUtils
import com.crosspaste.utils.ioDispatcher
import com.crosspaste.utils.namedScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import java.util.UUID

class HeadlessPeer(
    appInstanceId: String = UUID.randomUUID().toString(),
    userName: String = "e2e-peer",
    appVersion: String = "0.0.0-e2e",
    appRevision: String = "Unknown",
    pairingVersion: Int = SyncApi.PAIRING_VERSION,
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
            pairingVersion = pairingVersion,
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

    private val pairingV3Scope: CoroutineScope? =
        if (pairingVersion >= PairingV3.PROTOCOL_VERSION) {
            namedScope(ioDispatcher, "E2ePairingV3")
        } else {
            null
        }

    val pairingProtocolV3Service: PairingProtocolV3Service? =
        if (pairingVersion >= PairingV3.PROTOCOL_VERSION) {
            PairingProtocolV3Service(
                appInfo = appInfo,
                pairingV3ClientApi = PairingV3ClientApi(pasteClient, exceptionHandler),
                // Conformance/E2E only. Production intentionally registers
                // UnavailablePakeProvider pending constant-time EC review.
                pakeProvider = Spake2PakeProvider(BouncyCastlePakeEcOps()),
                receiptCache = PairingReceiptCache(),
                rateLimiter = PairingRateLimiter(),
                secureKeyPairSerializer = secureKeyPairSerializer,
                secureStore = secureStore,
                sessionStore = PairingSessionStore(),
                acceptanceWindow = PairingAcceptanceWindow(),
                isPairingV3Enabled = { true },
                scope = checkNotNull(pairingV3Scope),
            )
        } else {
            null
        }

    val pasteClientApi: PasteClientApi = PasteClientApi(pasteClient, configManager)

    val pullClientApi: PullClientApi = PullClientApi(pasteClient, configManager, exceptionHandler)

    val bonjourAdvertiser: BonjourAdvertiser = BonjourAdvertiser(appInfo).also { it.start() }

    fun close() {
        pairingV3Scope?.cancel()
        bonjourAdvertiser.close()
        pasteClient.close()
    }
}
