/**
 * Compact SDP codec for WebRTC DataChannel connections.
 *
 * Extracts the essential unique fields from an SDP + ICE candidates,
 * encodes them into a short base64 string (~120 chars) for manual exchange,
 * and reconstructs a valid SDP from the decoded fields.
 */

/** Compact representation of the unique SDP fields */
export interface CompactSDP {
  /** ICE ufrag */
  u: string;
  /** ICE pwd */
  p: string;
  /** DTLS fingerprint SHA-256 hex (no colons) */
  f: string;
  /** SDP setup role: "actpass" | "active" | "passive" */
  s: string;
  /** Host candidate IP */
  i: string;
  /** Host candidate port */
  o: number;
}

// ─── SDP Parsing ──────────────────────────────────────────────────────────

function sdpLine(sdp: string, prefix: string): string | null {
  for (const line of sdp.split("\r\n")) {
    if (line.startsWith(prefix)) return line.slice(prefix.length);
  }
  return null;
}

/**
 * Extract compact SDP fields from local description + gathered ICE candidates.
 * Call this AFTER ICE gathering is complete.
 */
export function extractCompact(sdp: string): CompactSDP {
  const ufrag = sdpLine(sdp, "a=ice-ufrag:");
  const pwd = sdpLine(sdp, "a=ice-pwd:");
  const fpLine = sdpLine(sdp, "a=fingerprint:sha-256 ");
  const setup = sdpLine(sdp, "a=setup:");

  if (!ufrag || !pwd || !fpLine || !setup) {
    throw new Error("Missing SDP fields");
  }

  // Remove colons from fingerprint
  const fp = fpLine.replace(/:/g, "");

  // Parse the first host candidate from the SDP
  let ip = "";
  let port = 0;
  for (const line of sdp.split("\r\n")) {
    if (line.startsWith("a=candidate:") && line.includes("typ host")) {
      const parts = line.split(" ");
      // a=candidate:foundation component protocol priority ip port typ host
      ip = parts[4];
      port = parseInt(parts[5], 10);
      // Skip link-local IPv6
      if (ip && !ip.startsWith("::") && !ip.startsWith("fe80")) break;
      ip = "";
      port = 0;
    }
  }

  if (!ip || !port) {
    throw new Error("No host ICE candidate found. Are you on a network?");
  }

  return { u: ufrag, p: pwd, f: fp, s: setup, i: ip, o: port };
}

// ─── Encoding ─────────────────────────────────────────────────────────────

/** Encode compact SDP to a short string for manual exchange */
export function encodeCompact(c: CompactSDP): string {
  return btoa(JSON.stringify(c));
}

/** Decode a compact string back to SDP fields */
export function decodeCompact(code: string): CompactSDP {
  try {
    return JSON.parse(atob(code.trim())) as CompactSDP;
  } catch {
    throw new Error("Invalid connection code");
  }
}

// ─── SDP Reconstruction ──────────────────────────────────────────────────

/**
 * Build a minimal valid SDP for a DataChannel connection from compact fields.
 * The type determines the o= session version and m= line format.
 */
export function buildSDP(c: CompactSDP, _type: "offer" | "answer"): string {
  // Reconstruct fingerprint with colons
  const fpHex = c.f.toUpperCase();
  const fpParts: string[] = [];
  for (let i = 0; i < fpHex.length; i += 2) {
    fpParts.push(fpHex.slice(i, i + 2));
  }
  const fingerprint = fpParts.join(":");

  const lines = [
    "v=0",
    `o=- ${Date.now()} 2 IN IP4 127.0.0.1`,
    "s=-",
    "t=0 0",
    "a=group:BUNDLE 0",
    "a=extmap-allow-mixed",
    "a=msid-semantic: WMS",
    "m=application 9 UDP/DTLS/SCTP webrtc-datachannel",
    `c=IN IP4 ${c.i}`,
    `a=ice-ufrag:${c.u}`,
    `a=ice-pwd:${c.p}`,
    "a=ice-options:trickle",
    `a=fingerprint:sha-256 ${fingerprint}`,
    `a=setup:${c.s}`,
    "a=mid:0",
    "a=sctp-port:5000",
    "a=max-message-size:262144",
    `a=candidate:1 1 udp 2130706431 ${c.i} ${c.o} typ host generation 0`,
  ];

  // SDP must end with \r\n
  return lines.join("\r\n") + "\r\n";
}

// ─── Format helpers ──────────────────────────────────────────────────────

/** Format code with dashes for readability */
export function formatCode(code: string): string {
  return code.replace(/(.{4})/g, "$1-").replace(/-$/, "");
}

/** Strip dashes from formatted code */
export function unformatCode(code: string): string {
  return code.replace(/-/g, "").trim();
}
