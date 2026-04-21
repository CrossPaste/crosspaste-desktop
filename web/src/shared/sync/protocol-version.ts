/**
 * Sync protocol version. Must match desktop `SyncApi.VERSION`
 * (app/src/commonMain/kotlin/com/crosspaste/net/SyncApi.kt).
 * Desktop uses strict integer equality — no forward compatibility.
 */
export const PROTOCOL_VERSION = 3;

export function isCompatibleVersion(remote: number): boolean {
  return Number.isInteger(remote) && remote === PROTOCOL_VERSION;
}
