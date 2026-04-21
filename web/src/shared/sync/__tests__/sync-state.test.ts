import { describe, it, expect } from "vitest";
import { SyncState, isReachable, isErrorState } from "../sync-state";

describe("SyncState", () => {
  it("has numeric values matching the desktop enum", () => {
    expect(SyncState.CONNECTED).toBe(0);
    expect(SyncState.CONNECTING).toBe(1);
    expect(SyncState.DISCONNECTED).toBe(2);
    expect(SyncState.UNMATCHED).toBe(3);
    expect(SyncState.UNVERIFIED).toBe(4);
    expect(SyncState.INCOMPATIBLE).toBe(5);
  });

  it("treats CONNECTED as the only reachable state", () => {
    expect(isReachable(SyncState.CONNECTED)).toBe(true);
    expect(isReachable(SyncState.CONNECTING)).toBe(false);
    expect(isReachable(SyncState.DISCONNECTED)).toBe(false);
    expect(isReachable(SyncState.UNMATCHED)).toBe(false);
    expect(isReachable(SyncState.UNVERIFIED)).toBe(false);
    expect(isReachable(SyncState.INCOMPATIBLE)).toBe(false);
  });

  it("classifies error-like states", () => {
    expect(isErrorState(SyncState.UNMATCHED)).toBe(true);
    expect(isErrorState(SyncState.INCOMPATIBLE)).toBe(true);
    expect(isErrorState(SyncState.DISCONNECTED)).toBe(true);
    expect(isErrorState(SyncState.CONNECTED)).toBe(false);
    expect(isErrorState(SyncState.CONNECTING)).toBe(false);
    expect(isErrorState(SyncState.UNVERIFIED)).toBe(false);
  });
});
