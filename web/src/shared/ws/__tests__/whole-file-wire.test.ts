import { CrossPasteHash } from "@/shared/core";
import { describe, expect, it } from "vitest";
import { createWholeFileRequest, isValidWholeFilePayload } from "../ws-message-handler";

describe("whole-file WebSocket wire", () => {
  it("includes the exact relative path while preserving the legacy basename", () => {
    expect(createWholeFileRequest(7, "paste-hash", "dir/file.txt")).toEqual({
      mode: "whole",
      id: 7,
      hash: "paste-hash",
      fileName: "file.txt",
      relativePath: "dir/file.txt",
    });
  });

  it("accepts only payloads matching the advertised file size and hash", () => {
    const payload = new TextEncoder().encode("expected");
    const bytes = new Int8Array(payload.buffer, payload.byteOffset, payload.byteLength);
    const metadata = {
      "dir/file.txt": {
        type: "file",
        size: payload.byteLength,
        hash: CrossPasteHash.hashBytes(bytes),
      },
    };

    expect(isValidWholeFilePayload(metadata, "dir/file.txt", payload)).toBe(true);
    expect(
      isValidWholeFilePayload(metadata, "dir/file.txt", new TextEncoder().encode("tampered")),
    ).toBe(false);
    expect(isValidWholeFilePayload(metadata, "other/file.txt", payload)).toBe(false);
  });
});
