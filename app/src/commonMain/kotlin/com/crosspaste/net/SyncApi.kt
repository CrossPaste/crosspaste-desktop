package com.crosspaste.net

import com.crosspaste.pairing.v3.PairingV3

object SyncApi {

    const val VERSION: Int = 3

    /**
     * The advertised pairing capability. Still 2: the v3 protocol surface exists
     * (see `PairingProtocolV3Service`) but stays unadvertised until the reviewed
     * SPAKE2 provider and the v3 UI ship (design doc §19 Phase 6 rollout flag).
     */
    const val PAIRING_VERSION: Int = 2

    fun supportsSASPairing(remotePairingVersion: Int?): Boolean =
        remotePairingVersion != null && remotePairingVersion >= 2

    fun supportsPairingV3(remotePairingVersion: Int?): Boolean =
        remotePairingVersion != null && remotePairingVersion >= PairingV3.PROTOCOL_VERSION

    val SCHEMA =
        """
        [
          {
            "path": "/pull/file",
            "method": "post",
            "receive": "PullFileRequest"
          },
          {
            "path": "/pull/icon/{source}",
            "method": "get"
          },
          {
            "path": "/pull/paste",
            "method": "get"
          },
          {
            "path": "/pull/pasteBatch",
            "method": "get"
          },
          {
            "path": "/sync/heartbeat",
            "method": "get"
          },
          {
            "path": "/sync/heartbeat/syncInfo",
            "method": "post",
            "receive": "SyncInfo"
          },
          {
            "path": "/sync/notifyExit",
            "method": "get"
          },
          {
            "path": "/sync/notifyRemove",
            "method": "get"
          },
          {
            "path": "/sync/pairing/v3/intent",
            "method": "post",
            "receive": "PairingIntentV3"
          },
          {
            "path": "/sync/pairing/v3/proof",
            "method": "post",
            "receive": "PairingProofV3"
          },
          {
            "path": "/sync/pairing/v3/commit",
            "method": "post",
            "receive": "PairingCommitV3"
          },
          {
            "path": "/sync/pairing/v3/cancel",
            "method": "post",
            "receive": "PairingCancelV3"
          },
          {
            "path": "/sync/paste",
            "method": "post",
            "receive": "PasteData"
          },
          {
            "path": "/sync/showToken",
            "method": "get"
          },
          {
            "path": "/sync/syncInfo",
            "method": "get"
          },
          {
            "path": "/sync/telnet",
            "method": "get"
          },
          {
            "path": "/sync/trust",
            "method": "post",
            "receive": "TrustRequest"
          },
          {
            "path": "/sync/trust/v2/exchange",
            "method": "post",
            "receive": "KeyExchangeRequest"
          },
          {
            "path": "/sync/trust/v2/confirm",
            "method": "post",
            "receive": "TrustConfirmRequest"
          }
        ]
        """.trimIndent()

    fun compareVersion(connectedVersion: Int): VersionRelation =
        when {
            VERSION < connectedVersion -> VersionRelation.LOWER_THAN
            VERSION == connectedVersion -> VersionRelation.EQUAL_TO
            else -> VersionRelation.HIGHER_THAN
        }
}

enum class VersionRelation {
    /**
     * Current version is lower than the connected version
     */
    LOWER_THAN,

    /**
     * Current version equals the connected version
     */
    EQUAL_TO,

    /**
     * Current version is higher than the connected version
     */
    HIGHER_THAN,
}
