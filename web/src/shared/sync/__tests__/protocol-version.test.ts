import { describe, it, expect } from "vitest";
import { PROTOCOL_VERSION, isCompatibleVersion } from "../protocol-version";

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
