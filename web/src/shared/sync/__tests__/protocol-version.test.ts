import { describe, it, expect } from "vitest";
import {
  PROTOCOL_VERSION,
  MAX_PAIRING_VERSION,
  isCompatibleVersion,
  selectPairingMode,
} from "../protocol-version";

describe("PROTOCOL_VERSION", () => {
  it("matches desktop SyncApi.VERSION (currently 3)", () => {
    expect(PROTOCOL_VERSION).toBe(3);
  });
});

describe("isCompatibleVersion", () => {
  it("returns true only for exact match", () => {
    expect(isCompatibleVersion(3)).toBe(true);
  });

  it("rejects lower versions", () => {
    expect(isCompatibleVersion(2)).toBe(false);
    expect(isCompatibleVersion(0)).toBe(false);
  });

  it("rejects higher versions", () => {
    expect(isCompatibleVersion(4)).toBe(false);
  });

  it("rejects NaN / non-integer", () => {
    expect(isCompatibleVersion(Number.NaN)).toBe(false);
    expect(isCompatibleVersion(3.5)).toBe(false);
  });
});

describe("MAX_PAIRING_VERSION", () => {
  it("matches desktop SyncApi.MAX_IMPLEMENTED_PAIRING_VERSION (currently 3)", () => {
    expect(MAX_PAIRING_VERSION).toBe(3);
  });
});

describe("selectPairingMode", () => {
  it("falls back to v1 when the peer advertises nothing", () => {
    expect(selectPairingMode(undefined, true)).toBe(1);
  });

  it("selects v1 for pre-SAS peers", () => {
    expect(selectPairingMode(0, true)).toBe(1);
    expect(selectPairingMode(1, true)).toBe(1);
  });

  it("selects v2 for SAS-capable peers", () => {
    expect(selectPairingMode(2, true)).toBe(2);
  });

  it("selects v3 for SPAKE2-capable peers, capped at our implementation", () => {
    expect(selectPairingMode(3, true)).toBe(3);
    // A future desktop advertising v4 still pairs with our best (v3).
    expect(selectPairingMode(4, true)).toBe(3);
  });

  it("caps at v2 while the v3 interop gate is closed (production fail-closed)", () => {
    expect(selectPairingMode(3, false)).toBe(2);
    expect(selectPairingMode(4, false)).toBe(2);
    expect(selectPairingMode(2, false)).toBe(2);
    expect(selectPairingMode(undefined, false)).toBe(1);
  });

  it("rejects malformed advertisements", () => {
    expect(selectPairingMode(Number.NaN, true)).toBe(1);
    expect(selectPairingMode(2.5, true)).toBe(1);
  });
});
