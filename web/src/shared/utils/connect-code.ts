/**
 * Crockford Base32 encoding for IP:port → short connection code.
 *
 * Encodes 6 bytes (IPv4 + port) into a 10-character code formatted as XXXXX-XXXXX.
 * Case-insensitive, excludes I/L/O/U to avoid human confusion.
 *
 * @see https://www.crockford.com/base32.html
 */

const ENCODE_CHARS = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";

const DECODE_MAP: Record<string, number> = {};
for (let i = 0; i < ENCODE_CHARS.length; i++) {
  DECODE_MAP[ENCODE_CHARS[i]] = i;
}
// Accept lowercase and common misreads
DECODE_MAP["O"] = 0; // O → 0
DECODE_MAP["o"] = 0;
DECODE_MAP["I"] = 1; // I → 1
DECODE_MAP["i"] = 1;
DECODE_MAP["L"] = 1; // L → 1
DECODE_MAP["l"] = 1;
for (let i = 0; i < ENCODE_CHARS.length; i++) {
  DECODE_MAP[ENCODE_CHARS[i].toLowerCase()] = i;
}

/**
 * Encode an IPv4 address and port into a 10-char Crockford Base32 code.
 * Returns formatted as "XXXXX-XXXXX".
 */
export function encodeConnectCode(ip: string, port: number): string {
  const parts = ip.split(".").map(Number);
  if (parts.length !== 4 || parts.some((p) => isNaN(p) || p < 0 || p > 255)) {
    throw new Error("Invalid IPv4 address");
  }
  if (port < 1 || port > 65535) {
    throw new Error("Invalid port");
  }

  // Pack into 48-bit BigInt: [IP0][IP1][IP2][IP3][PortHigh][PortLow]
  const value =
    (BigInt(parts[0]) << 40n) |
    (BigInt(parts[1]) << 32n) |
    (BigInt(parts[2]) << 24n) |
    (BigInt(parts[3]) << 16n) |
    BigInt(port);

  // Encode to 10 chars of Crockford Base32 (5 bits each, 50 bits, top 2 always 0)
  let code = "";
  let remaining = value;
  for (let i = 0; i < 10; i++) {
    code = ENCODE_CHARS[Number(remaining & 31n)] + code;
    remaining >>= 5n;
  }

  return code.slice(0, 5) + "-" + code.slice(5);
}

/**
 * Decode a Crockford Base32 connection code back to IP and port.
 * Accepts with or without dash, case-insensitive.
 */
export function decodeConnectCode(code: string): { ip: string; port: number } {
  const cleaned = code.replace(/[-\s]/g, "").toUpperCase();
  if (cleaned.length !== 10) {
    throw new Error("Connection code must be 10 characters");
  }

  let value = 0n;
  for (const ch of cleaned) {
    const digit = DECODE_MAP[ch];
    if (digit === undefined) {
      throw new Error(`Invalid character in code: ${ch}`);
    }
    value = (value << 5n) | BigInt(digit);
  }

  const port = Number(value & 0xFFFFn);
  const ip3 = Number((value >> 16n) & 0xFFn);
  const ip2 = Number((value >> 24n) & 0xFFn);
  const ip1 = Number((value >> 32n) & 0xFFn);
  const ip0 = Number((value >> 40n) & 0xFFn);

  if (port < 1 || port > 65535) {
    throw new Error("Invalid connection code: port out of range");
  }

  return { ip: `${ip0}.${ip1}.${ip2}.${ip3}`, port };
}

/**
 * Check if a string looks like a connection code (10 base32 chars, with optional dash).
 */
export function isConnectCode(input: string): boolean {
  const cleaned = input.replace(/[-\s]/g, "");
  if (cleaned.length !== 10) return false;
  return /^[0-9A-Za-z]{10}$/.test(cleaned);
}
