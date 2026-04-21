import { describe, it, expect, beforeEach, afterEach, vi } from "vitest";
import { apiGet } from "../client";
import { SyncApiError, StandardErrorCode } from "../sync-error";

const CONFIG = { host: "127.0.0.1", port: 13129, appInstanceId: "ext-1" };

function mockFetchOnce(response: {
  ok: boolean;
  status: number;
  body: string;
}): void {
  globalThis.fetch = vi.fn().mockResolvedValueOnce({
    ok: response.ok,
    status: response.status,
    text: () => Promise.resolve(response.body),
  } as unknown as Response);
}

describe("apiGet error handling", () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("throws SyncApiError with errorCode when body is a FailResponse", async () => {
    mockFetchOnce({
      ok: false,
      status: 400,
      body: '{"errorCode":2008,"message":"DECRYPT_FAIL"}',
    });

    await expect(apiGet(CONFIG, "/sync/heartbeat")).rejects.toMatchObject({
      name: "SyncApiError",
      errorCode: StandardErrorCode.DECRYPT_FAIL,
      status: 400,
      path: "/sync/heartbeat",
    });
  });

  it("throws generic Error when body is not a FailResponse", async () => {
    mockFetchOnce({ ok: false, status: 500, body: "boom" });

    const err: unknown = await apiGet(CONFIG, "/sync/telnet").catch((e) => e);
    expect(err).toBeInstanceOf(Error);
    expect(err).not.toBeInstanceOf(SyncApiError);
    expect(err instanceof Error && err.message).toContain("boom");
  });

  it("returns parsed integer body on success", async () => {
    mockFetchOnce({ ok: true, status: 200, body: "3" });
    const result = await apiGet<number>(CONFIG, "/sync/telnet");
    expect(result).toBe(3);
  });
});
