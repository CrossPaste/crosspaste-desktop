import { describe, expect, it } from "vitest";
import { ECHO_SUPPRESS_MS, shouldWriteRemotePaste } from "../paste-push-policy";

describe("shouldWriteRemotePaste", () => {
  const now = 1_000_000;

  it("writes a genuinely new paste", () => {
    expect(
      shouldWriteRemotePaste({ hash: "aaa", lastHash: "bbb", priorReceivedAt: null, now }),
    ).toBe(true);
  });

  it("skips when clipboard already holds this hash per our own tracking", () => {
    expect(
      shouldWriteRemotePaste({ hash: "aaa", lastHash: "aaa", priorReceivedAt: null, now }),
    ).toBe(false);
  });

  it("skips an echo: same hash ingested moments ago", () => {
    expect(
      shouldWriteRemotePaste({
        hash: "aaa",
        lastHash: "bbb",
        priorReceivedAt: now - ECHO_SUPPRESS_MS + 1000,
        now,
      }),
    ).toBe(false);
  });

  it("writes a legitimate re-copy: same hash but ingested long ago", () => {
    expect(
      shouldWriteRemotePaste({
        hash: "aaa",
        lastHash: "bbb",
        priorReceivedAt: now - ECHO_SUPPRESS_MS - 1000,
        now,
      }),
    ).toBe(true);
  });

  it("treats the exact window boundary as no longer an echo", () => {
    expect(
      shouldWriteRemotePaste({
        hash: "aaa",
        lastHash: "bbb",
        priorReceivedAt: now - ECHO_SUPPRESS_MS,
        now,
      }),
    ).toBe(true);
  });
});
