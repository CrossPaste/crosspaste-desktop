/**
 * Device sync states, numerically aligned with the desktop
 * `com.crosspaste.db.sync.SyncState` constants. Do NOT reorder.
 */
export enum SyncState {
  CONNECTED = 0,
  CONNECTING = 1,
  DISCONNECTED = 2,
  UNMATCHED = 3,
  UNVERIFIED = 4,
  INCOMPATIBLE = 5,
}

export function isReachable(state: SyncState): boolean {
  return state === SyncState.CONNECTED;
}

export function isErrorState(state: SyncState): boolean {
  return (
    state === SyncState.DISCONNECTED ||
    state === SyncState.UNMATCHED ||
    state === SyncState.INCOMPATIBLE
  );
}
