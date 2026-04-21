import { describe, it, expect } from "vitest";
import {
  SyncApiError,
  StandardErrorCode,
  parseFailResponse,
} from "../sync-error";

describe("StandardErrorCode", () => {
  it("matches desktop numeric codes", () => {
    expect(StandardErrorCode.TOKEN_INVALID).toBe(1001);
    expect(StandardErrorCode.NOT_MATCH_APP_INSTANCE_ID).toBe(1011);
    expect(StandardErrorCode.SIGN_INVALID).toBe(2000);
    expect(StandardErrorCode.TRUST_FAIL).toBe(2005);
    expect(StandardErrorCode.DECRYPT_FAIL).toBe(2008);
  });
});

describe("parseFailResponse", () => {
  it("parses a valid FailResponse body", () => {
    const body = '{"errorCode":2008,"message":"DECRYPT_FAIL"}';
    const parsed = parseFailResponse(body);
    expect(parsed).toEqual({ errorCode: 2008, message: "DECRYPT_FAIL" });
  });

  it("parses a FailResponse with empty message", () => {
    const body = '{"errorCode":1001,"message":""}';
    const parsed = parseFailResponse(body);
    expect(parsed).toEqual({ errorCode: 1001, message: "" });
  });

  it("returns null for non-JSON body", () => {
    expect(parseFailResponse("plain text")).toBeNull();
    expect(parseFailResponse("")).toBeNull();
  });

  it("returns null for JSON missing errorCode", () => {
    expect(parseFailResponse('{"foo":"bar"}')).toBeNull();
    expect(parseFailResponse('{"errorCode":"not-a-number"}')).toBeNull();
  });
});

describe("SyncApiError", () => {
  it("preserves errorCode, status, path, and message", () => {
    const err = new SyncApiError({
      errorCode: 2008,
      status: 400,
      path: "/sync/heartbeat",
      message: "DECRYPT_FAIL",
    });
    expect(err).toBeInstanceOf(Error);
    expect(err.errorCode).toBe(2008);
    expect(err.status).toBe(400);
    expect(err.path).toBe("/sync/heartbeat");
    expect(err.message).toBe("DECRYPT_FAIL");
    expect(err.name).toBe("SyncApiError");
  });

  it("isDecryptFail() is true for DECRYPT_FAIL", () => {
    const err = new SyncApiError({
      errorCode: 2008,
      status: 400,
      path: "/sync/heartbeat",
      message: "",
    });
    expect(err.isDecryptFail()).toBe(true);
  });

  it("isDecryptFail() is false for other codes", () => {
    const err = new SyncApiError({
      errorCode: 1001,
      status: 400,
      path: "/sync/trust",
      message: "",
    });
    expect(err.isDecryptFail()).toBe(false);
  });
});
