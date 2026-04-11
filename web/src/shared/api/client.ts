export interface RequestConfig {
  host: string;
  port: number;
  appInstanceId: string;
  targetAppInstanceId?: string;
}

function baseUrl(config: RequestConfig): string {
  return `http://${config.host}:${config.port}`;
}

function defaultHeaders(config: RequestConfig): Record<string, string> {
  const headers: Record<string, string> = {
    "Content-Type": "application/json",
    appInstanceId: config.appInstanceId,
  };
  if (config.targetAppInstanceId) {
    headers.targetAppInstanceId = config.targetAppInstanceId;
  }
  return headers;
}

/**
 * Parse the response body, handling JSON, plain text/numbers, and empty bodies.
 * The server uses `successResponse(call)` for empty, `successResponse(call, value)` for data.
 */
async function parseResponse<T>(res: Response): Promise<T> {
  const text = await res.text();
  if (!text) return undefined as T;
  try {
    return JSON.parse(text) as T;
  } catch {
    // Plain value (e.g. integer VERSION)
    return text as T;
  }
}

export async function apiGet<T>(
  config: RequestConfig,
  path: string,
  extraHeaders?: Record<string, string>,
): Promise<T> {
  const url = `${baseUrl(config)}${path}`;
  const res = await fetch(url, {
    method: "GET",
    headers: { ...defaultHeaders(config), ...extraHeaders },
  });
  if (!res.ok) {
    const body = await res.text();
    throw new Error(`GET ${path} failed (${res.status}): ${body}`);
  }
  return parseResponse<T>(res);
}

export async function apiGetText(
  config: RequestConfig,
  path: string,
  extraHeaders?: Record<string, string>,
): Promise<string> {
  const url = `${baseUrl(config)}${path}`;
  const res = await fetch(url, {
    method: "GET",
    headers: { ...defaultHeaders(config), ...extraHeaders },
  });
  if (!res.ok) {
    const body = await res.text();
    throw new Error(`GET ${path} failed (${res.status}): ${body}`);
  }
  return res.text();
}

export async function apiPost<T>(
  config: RequestConfig,
  path: string,
  body?: unknown,
  extraHeaders?: Record<string, string>,
): Promise<T> {
  const url = `${baseUrl(config)}${path}`;
  const res = await fetch(url, {
    method: "POST",
    headers: { ...defaultHeaders(config), ...extraHeaders },
    body: body ? JSON.stringify(body) : undefined,
  });
  if (!res.ok) {
    const text = await res.text();
    throw new Error(`POST ${path} failed (${res.status}): ${text}`);
  }
  return parseResponse<T>(res);
}
