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

/**
 * Pairing v3 interop gate, mirroring the desktop's fail-closed rollout
 * (`SyncApi.PAIRING_VERSION = 2` until every shipping platform has a reviewed
 * constant-time SPAKE2 backend — Phase 0 ADR). The JS backend (noble) is not
 * constant-time, so production builds cap at v2; v3 activates only in dev
 * builds or when explicitly built with VITE_PAIRING_V3_INTEROP=true.
 */
export const PAIRING_V3_ENABLED: boolean =
  import.meta.env?.DEV === true || import.meta.env?.VITE_PAIRING_V3_INTEROP === "true";

/** The pairing version this build advertises in its own AppInfo. */
export const ADVERTISED_PAIRING_VERSION: number = PAIRING_V3_ENABLED ? MAX_PAIRING_VERSION : 2;

/** The pairing protocol to use against a peer advertising `remotePairingVersion`. */
export function selectPairingMode(
  remotePairingVersion: number | undefined,
  v3Enabled: boolean = PAIRING_V3_ENABLED,
): 1 | 2 | 3 {
  if (remotePairingVersion === undefined || !Number.isInteger(remotePairingVersion)) return 1;
  if (remotePairingVersion >= 3 && v3Enabled) return 3;
  if (remotePairingVersion >= 2) return 2;
  return 1;
}
