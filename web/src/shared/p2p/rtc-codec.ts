/**
 * Compact SDP codec for WebRTC DataChannel connections.
 *
 * Transmits the full browser-generated SDP via base64 encoding.
 * This avoids SDP reconstruction issues — the browser's own SDP is always valid.
 */

/** Encode a full SDP string to a compact code for manual exchange */
export function encodeSDP(sdp: string): string {
  return btoa(sdp);
}

/** Decode a compact code back to a full SDP string */
export function decodeSDP(code: string): string {
  try {
    return atob(code.replace(/[\s-]/g, ""));
  } catch {
    throw new Error("Invalid connection code");
  }
}

/** Format code with line breaks for readability (every 60 chars) */
export function formatCode(code: string): string {
  return code.replace(/(.{60})/g, "$1\n").trim();
}
