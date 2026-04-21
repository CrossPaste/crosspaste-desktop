import { describe, it, expect } from "vitest";
import { deriveSyncState, type DeviceRuntimeFacts } from "../derive-state";
import { SyncState } from "../sync-state";

const FRESH_NOW = 1_700_000_000_000;
const STALE_HTTP = FRESH_NOW - 120_000;
const FRESH_HTTP = FRESH_NOW - 10_000;

function facts(partial: Partial<DeviceRuntimeFacts>): DeviceRuntimeFacts {
  return {
    wsState: null,
    lastHttpSuccessAt: null,
    lastErrorCode: null,
    versionDrift: false,
    needsRePair: false,
    connecting: false,
    ...partial,
  };
}

describe("deriveSyncState", () => {
  it("INCOMPATIBLE wins over everything when version drifts", () => {
    expect(
      deriveSyncState(
        facts({
          versionDrift: true,
          wsState: "ws_connected",
          lastHttpSuccessAt: FRESH_HTTP,
        }),
        FRESH_NOW,
      ),
    ).toBe(SyncState.INCOMPATIBLE);
  });

  it("UNVERIFIED when needsRePair flag is set", () => {
    expect(
      deriveSyncState(facts({ needsRePair: true }), FRESH_NOW),
    ).toBe(SyncState.UNVERIFIED);
  });

  it("UNMATCHED when lastErrorCode is DECRYPT_FAIL", () => {
    expect(
      deriveSyncState(facts({ lastErrorCode: 2008 }), FRESH_NOW),
    ).toBe(SyncState.UNMATCHED);
  });

  it("CONNECTED when WS is connected", () => {
    expect(
      deriveSyncState(facts({ wsState: "ws_connected" }), FRESH_NOW),
    ).toBe(SyncState.CONNECTED);
  });

  it("CONNECTED when HTTP is fresh", () => {
    expect(
      deriveSyncState(
        facts({ lastHttpSuccessAt: FRESH_HTTP }),
        FRESH_NOW,
      ),
    ).toBe(SyncState.CONNECTED);
  });

  it("CONNECTING while an attempt is in progress and nothing healthy", () => {
    expect(
      deriveSyncState(facts({ connecting: true }), FRESH_NOW),
    ).toBe(SyncState.CONNECTING);
  });

  it("CONNECTING while WS is reconnecting and no fresh HTTP", () => {
    expect(
      deriveSyncState(facts({ wsState: "ws_reconnecting" }), FRESH_NOW),
    ).toBe(SyncState.CONNECTING);
  });

  it("DISCONNECTED when stale HTTP and no WS", () => {
    expect(
      deriveSyncState(
        facts({ lastHttpSuccessAt: STALE_HTTP }),
        FRESH_NOW,
      ),
    ).toBe(SyncState.DISCONNECTED);
  });

  it("DISCONNECTED on bare runtime", () => {
    expect(deriveSyncState(facts({}), FRESH_NOW)).toBe(
      SyncState.DISCONNECTED,
    );
  });

  it("clears UNMATCHED once WS reconnects", () => {
    // When the service worker observes a healthy channel, it is expected
    // to null-out lastErrorCode before calling derive. This test documents
    // that contract: if lastErrorCode is null and WS is good, CONNECTED.
    expect(
      deriveSyncState(
        facts({ lastErrorCode: null, wsState: "ws_connected" }),
        FRESH_NOW,
      ),
    ).toBe(SyncState.CONNECTED);
  });
});
