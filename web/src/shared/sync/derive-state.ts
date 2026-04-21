import type { WsConnectionStatus } from "@/shared/ws/ws-types";
import { StandardErrorCode } from "@/shared/api/sync-error";
import { SyncState } from "./sync-state";

/** HTTP heartbeat fires every 60s; allow a small grace window for jitter. */
export const FRESH_THRESHOLD_MS = 90_000;

export interface DeviceRuntimeFacts {
  wsState: WsConnectionStatus | null;
  lastHttpSuccessAt: number | null;
  /** StandardErrorCode from the most recent HTTP failure, or null. */
  lastErrorCode: number | null;
  /** Telnet/heartbeat returned a VERSION != PROTOCOL_VERSION. */
  versionDrift: boolean;
  /** Persisted hint: keys were wiped after UNMATCHED; user must re-pair. */
  needsRePair: boolean;
  /** Transient: handleConnect/handlePair currently in flight. */
  connecting: boolean;
}

/**
 * Priority order:
 *   1. INCOMPATIBLE — a protocol-level fact, overrides any liveness signal.
 *   2. UNVERIFIED   — waiting for re-pair after auto-wipe.
 *   3. UNMATCHED    — known crypto mismatch; recoverable only by re-pairing.
 *   4. CONNECTED    — either channel fresh.
 *   5. CONNECTING   — an attempt is in progress.
 *   6. DISCONNECTED — default / stale.
 */
export function deriveSyncState(
  facts: DeviceRuntimeFacts,
  now: number,
): SyncState {
  if (facts.versionDrift) return SyncState.INCOMPATIBLE;
  if (facts.needsRePair) return SyncState.UNVERIFIED;
  if (facts.lastErrorCode === StandardErrorCode.DECRYPT_FAIL) {
    return SyncState.UNMATCHED;
  }

  if (facts.wsState === "ws_connected") return SyncState.CONNECTED;
  if (
    facts.lastHttpSuccessAt !== null &&
    now - facts.lastHttpSuccessAt < FRESH_THRESHOLD_MS
  ) {
    return SyncState.CONNECTED;
  }

  if (facts.connecting) return SyncState.CONNECTING;
  if (facts.wsState === "ws_reconnecting") return SyncState.CONNECTING;

  return SyncState.DISCONNECTED;
}
