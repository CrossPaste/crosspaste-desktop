/**
 * Mirror of desktop `com.crosspaste.exception.StandardErrorCode`.
 * Only the codes the extension can encounter on /sync/* routes.
 */
export const StandardErrorCode = {
  NOT_FOUND_APP_INSTANCE_ID: 1000,
  TOKEN_INVALID: 1001,
  NOT_MATCH_APP_INSTANCE_ID: 1011,
  SIGN_INVALID: 2000,
  EXCHANGE_TIMEOUT: 2002,
  UNTRUSTED_IDENTITY: 2003,
  TRUST_FAIL: 2005,
  DECRYPT_FAIL: 2008,
  REMOTE_SHOW_PAIRING_CODE_DISABLED: 3100,
} as const;

export interface FailResponse {
  errorCode: number;
  message: string;
}

export function parseFailResponse(body: string): FailResponse | null {
  if (!body) return null;
  let parsed: unknown;
  try {
    parsed = JSON.parse(body);
  } catch {
    return null;
  }
  if (
    typeof parsed !== "object" ||
    parsed === null ||
    typeof (parsed as { errorCode?: unknown }).errorCode !== "number"
  ) {
    return null;
  }
  const record = parsed as { errorCode: number; message?: unknown };
  return {
    errorCode: record.errorCode,
    message: typeof record.message === "string" ? record.message : "",
  };
}

export class SyncApiError extends Error {
  readonly errorCode: number;
  readonly status: number;
  readonly path: string;

  constructor(params: {
    errorCode: number;
    status: number;
    path: string;
    message: string;
  }) {
    super(params.message);
    this.name = "SyncApiError";
    this.errorCode = params.errorCode;
    this.status = params.status;
    this.path = params.path;
  }

  isDecryptFail(): boolean {
    return this.errorCode === StandardErrorCode.DECRYPT_FAIL;
  }
}
