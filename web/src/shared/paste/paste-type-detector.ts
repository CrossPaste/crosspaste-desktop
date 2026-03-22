import { PasteTypeInt } from "@/shared/models/paste-item";
import type { PasteItem } from "@/shared/models/paste-item";

/**
 * Detect the paste type from clipboard text content.
 * Returns { pasteType, pasteItem } with the appropriate type and item structure.
 *
 * Detection order (highest priority first):
 *  1. URL  — single-line text matching URL patterns
 *  2. Color — text matching color formats (hex, rgb, hsl)
 *  3. Text — default fallback
 */
export function detectPasteType(text: string): {
  pasteType: number;
  pasteItem: PasteItem;
} {
  const trimmed = text.trim();
  const size = new TextEncoder().encode(text).length;
  const hash = ""; // caller sets this

  // 1. URL detection — must be single-line
  if (!trimmed.includes("\n") && isUrl(trimmed)) {
    return {
      pasteType: PasteTypeInt.URL,
      pasteItem: {
        type: "url",
        identifiers: ["text/plain"],
        hash,
        size,
        url: trimmed,
      },
    };
  }

  // 2. Color detection — must be single-line, exact match
  if (!trimmed.includes("\n")) {
    const color = parseColor(trimmed);
    if (color !== null) {
      return {
        pasteType: PasteTypeInt.COLOR,
        pasteItem: {
          type: "color",
          identifiers: ["text/plain"],
          hash,
          size,
          color,
        },
      };
    }
  }

  // 3. Default: Text
  return {
    pasteType: PasteTypeInt.TEXT,
    pasteItem: {
      type: "text",
      identifiers: ["text/plain"],
      hash,
      size,
      text,
      extraInfo: null,
    },
  };
}

// ─── URL detection ──────────────────────────────────────────────────────

const URL_REGEX =
  /^https?:\/\/[^\s/$.?#].\S*$/i;

function isUrl(text: string): boolean {
  // Standard http(s) URLs
  if (URL_REGEX.test(text)) return true;

  // Try parsing with URL constructor for broader coverage
  try {
    const url = new URL(text);
    return url.protocol === "http:" || url.protocol === "https:";
  } catch {
    return false;
  }
}

// ─── Color detection ────────────────────────────────────────────────────

/**
 * Parse a color string and return a 32-bit ARGB integer, or null if not a color.
 * Supports: #RGB, #RRGGBB, #RRGGBBAA, rgb(), rgba(), hsl(), hsla()
 */
export function parseColor(text: string): number | null {
  // Hex colors
  const hex = parseHexColor(text);
  if (hex !== null) return hex;

  // rgb/rgba
  const rgb = parseRgbColor(text);
  if (rgb !== null) return rgb;

  // hsl/hsla
  const hsl = parseHslColor(text);
  if (hsl !== null) return hsl;

  return null;
}

/**
 * Parse hex color, matching desktop ColorParser.parseHexColor exactly.
 * Accepts with or without # prefix.
 * With #: 3,4,6,8 digits. Without #: 6,8 digits only (avoids "ABC" false positives).
 */
function parseHexColor(text: string): number | null {
  const hasHash = text.startsWith("#");
  const cleanHex = hasHash ? text.slice(1) : text;

  // Without # prefix, only accept 6 or 8 digit hex
  if (!hasHash && cleanHex.length !== 6 && cleanHex.length !== 8) return null;

  // Validate all chars are hex
  if (!/^[0-9a-f]+$/i.test(cleanHex)) return null;

  let r: number, g: number, b: number, a = 255;

  switch (cleanHex.length) {
    case 3: // #RGB
      r = parseInt(cleanHex[0] + cleanHex[0], 16);
      g = parseInt(cleanHex[1] + cleanHex[1], 16);
      b = parseInt(cleanHex[2] + cleanHex[2], 16);
      break;
    case 4: // #RGBA
      r = parseInt(cleanHex[0] + cleanHex[0], 16);
      g = parseInt(cleanHex[1] + cleanHex[1], 16);
      b = parseInt(cleanHex[2] + cleanHex[2], 16);
      a = parseInt(cleanHex[3] + cleanHex[3], 16);
      break;
    case 6: // RRGGBB
      r = parseInt(cleanHex.slice(0, 2), 16);
      g = parseInt(cleanHex.slice(2, 4), 16);
      b = parseInt(cleanHex.slice(4, 6), 16);
      break;
    case 8: // RRGGBBAA
      r = parseInt(cleanHex.slice(0, 2), 16);
      g = parseInt(cleanHex.slice(2, 4), 16);
      b = parseInt(cleanHex.slice(4, 6), 16);
      a = parseInt(cleanHex.slice(6, 8), 16);
      break;
    default:
      return null;
  }

  if (isNaN(r) || isNaN(g) || isNaN(b) || isNaN(a)) return null;

  return toArgb(a, r, g, b);
}

const RGB_REGEX =
  /^rgba?\(\s*(\d{1,3})\s*,\s*(\d{1,3})\s*,\s*(\d{1,3})\s*(?:,\s*([\d.]+)\s*)?\)$/i;

function parseRgbColor(text: string): number | null {
  const match = text.match(RGB_REGEX);
  if (!match) return null;

  const r = parseInt(match[1], 10);
  const g = parseInt(match[2], 10);
  const b = parseInt(match[3], 10);
  const a = match[4] !== undefined ? Math.round(parseFloat(match[4]) * 255) : 255;

  if (r > 255 || g > 255 || b > 255 || a > 255) return null;
  return toArgb(a, r, g, b);
}

const HSL_REGEX =
  /^hsla?\(\s*(\d{1,3})\s*,\s*([\d.]+)%\s*,\s*([\d.]+)%\s*(?:,\s*([\d.]+)\s*)?\)$/i;

function parseHslColor(text: string): number | null {
  const match = text.match(HSL_REGEX);
  if (!match) return null;

  const h = parseInt(match[1], 10);
  const s = parseFloat(match[2]) / 100;
  const l = parseFloat(match[3]) / 100;
  const a = match[4] !== undefined ? Math.round(parseFloat(match[4]) * 255) : 255;

  if (h > 360 || s > 1 || l > 1 || a > 255) return null;

  const [r, g, b] = hslToRgb(h, s, l);
  return toArgb(a, r, g, b);
}

function hslToRgb(h: number, s: number, l: number): [number, number, number] {
  const c = (1 - Math.abs(2 * l - 1)) * s;
  const x = c * (1 - Math.abs(((h / 60) % 2) - 1));
  const m = l - c / 2;

  let r = 0, g = 0, b = 0;
  if (h < 60) { r = c; g = x; }
  else if (h < 120) { r = x; g = c; }
  else if (h < 180) { g = c; b = x; }
  else if (h < 240) { g = x; b = c; }
  else if (h < 300) { r = x; b = c; }
  else { r = c; b = x; }

  return [
    Math.round((r + m) * 255),
    Math.round((g + m) * 255),
    Math.round((b + m) * 255),
  ];
}

/** Pack ARGB components into a 32-bit signed integer (matches Kotlin Int) */
function toArgb(a: number, r: number, g: number, b: number): number {
  return ((a << 24) | (r << 16) | (g << 8) | b) | 0;
}
