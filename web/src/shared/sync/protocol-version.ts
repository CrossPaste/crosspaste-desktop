/**
 * Sync protocol version. Must match desktop `SyncApi.VERSION`
 * (app/src/commonMain/kotlin/com/crosspaste/net/SyncApi.kt).
 * Desktop uses strict integer equality — no forward compatibility.
 */
export const PROTOCOL_VERSION = 3;

export function isCompatibleVersion(remote: number): boolean {
  return Number.isInteger(remote) && remote === PROTOCOL_VERSION;
}

/**
 * Highest PAIRING protocol this extension implements. A separate version axis
 * from the sync transport version above: it travels in `AppInfo.pairingVersion`
 * and selects the trust handshake (1 = bearer token, 2 = SAS/ECDH,
 * 3 = SPAKE2+PIN). Mirrors desktop `SyncApi.MAX_IMPLEMENTED_PAIRING_VERSION`.
 */
export const MAX_PAIRING_VERSION = 3;

/** The pairing protocol to use against a peer advertising `remotePairingVersion`. */
export function selectPairingMode(remotePairingVersion: number | undefined): 1 | 2 | 3 {
  if (remotePairingVersion === undefined || !Number.isInteger(remotePairingVersion)) return 1;
  if (remotePairingVersion >= 3) return 3;
  if (remotePairingVersion >= 2) return 2;
  return 1;
}
