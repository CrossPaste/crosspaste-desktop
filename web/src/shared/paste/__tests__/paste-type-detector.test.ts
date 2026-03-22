import { describe, it, expect } from "vitest";
import { parseColor, detectPasteType } from "../paste-type-detector";
import { PasteTypeInt } from "@/shared/models/paste-item";

// ─── Helper: ARGB integer from components ─────────────────────────────
function argb(a: number, r: number, g: number, b: number): number {
  return ((a << 24) | (r << 16) | (g << 8) | b) | 0;
}

// ─── parseColor ───────────────────────────────────────────────────────

describe("parseColor", () => {
  describe("hex with # prefix", () => {
    it("parses #RRGGBB", () => {
      expect(parseColor("#FF0000")).toBe(argb(255, 255, 0, 0));
      expect(parseColor("#00FF00")).toBe(argb(255, 0, 255, 0));
      expect(parseColor("#0000FF")).toBe(argb(255, 0, 0, 255));
    });

    it("parses #RGB shorthand", () => {
      expect(parseColor("#F00")).toBe(argb(255, 255, 0, 0));
      expect(parseColor("#0F0")).toBe(argb(255, 0, 255, 0));
    });

    it("parses #RGBA shorthand", () => {
      expect(parseColor("#F008")).toBe(argb(0x88, 255, 0, 0));
    });

    it("parses #RRGGBBAA", () => {
      expect(parseColor("#FF000080")).toBe(argb(0x80, 255, 0, 0));
    });

    it("is case-insensitive", () => {
      expect(parseColor("#ff0000")).toBe(argb(255, 255, 0, 0));
      expect(parseColor("#Ff0000")).toBe(argb(255, 255, 0, 0));
    });
  });

  describe("hex without # prefix (6 or 8 digits only)", () => {
    it("parses FAACBF as color", () => {
      // This was the original bug — bare 6-digit hex was not recognized
      expect(parseColor("FAACBF")).toBe(argb(255, 0xfa, 0xac, 0xbf));
    });

    it("parses 6-digit bare hex", () => {
      expect(parseColor("FF0000")).toBe(argb(255, 255, 0, 0));
      expect(parseColor("00FF00")).toBe(argb(255, 0, 255, 0));
      expect(parseColor("000000")).toBe(argb(255, 0, 0, 0));
      expect(parseColor("FFFFFF")).toBe(argb(255, 255, 255, 255));
    });

    it("parses 8-digit bare hex", () => {
      expect(parseColor("FF000080")).toBe(argb(0x80, 255, 0, 0));
    });

    it("is case-insensitive", () => {
      expect(parseColor("faacbf")).toBe(argb(255, 0xfa, 0xac, 0xbf));
      expect(parseColor("FaAcBf")).toBe(argb(255, 0xfa, 0xac, 0xbf));
    });

    it("rejects 3-digit bare hex (avoids false positives like ABC)", () => {
      expect(parseColor("F00")).toBeNull();
      expect(parseColor("ABC")).toBeNull();
    });

    it("rejects 4-digit bare hex", () => {
      expect(parseColor("F008")).toBeNull();
    });

    it("rejects 5-digit bare hex", () => {
      expect(parseColor("12345")).toBeNull();
    });

    it("rejects 7-digit bare hex", () => {
      expect(parseColor("1234567")).toBeNull();
    });
  });

  describe("invalid hex", () => {
    it("rejects non-hex characters", () => {
      expect(parseColor("#GGGGGG")).toBeNull();
      expect(parseColor("#ZZZZZZ")).toBeNull();
      expect(parseColor("GHIJKL")).toBeNull();
    });

    it("rejects wrong-length with #", () => {
      expect(parseColor("#12345")).toBeNull();
      expect(parseColor("#1234567")).toBeNull();
    });

    it("rejects empty", () => {
      expect(parseColor("")).toBeNull();
      expect(parseColor("#")).toBeNull();
    });
  });

  describe("rgb() format", () => {
    it("parses rgb(r, g, b)", () => {
      expect(parseColor("rgb(255, 0, 0)")).toBe(argb(255, 255, 0, 0));
      expect(parseColor("rgb(0, 255, 0)")).toBe(argb(255, 0, 255, 0));
      expect(parseColor("rgb(0, 0, 255)")).toBe(argb(255, 0, 0, 255));
    });

    it("parses rgb with spaces", () => {
      expect(parseColor("rgb( 255 , 0 , 0 )")).toBe(argb(255, 255, 0, 0));
    });

    it("rejects out-of-range values", () => {
      expect(parseColor("rgb(256, 0, 0)")).toBeNull();
    });

    it("rejects missing values", () => {
      expect(parseColor("rgb(0, 0)")).toBeNull();
    });
  });

  describe("rgba() format", () => {
    it("parses rgba with alpha", () => {
      expect(parseColor("rgba(255, 0, 0, 1)")).toBe(argb(255, 255, 0, 0));
      expect(parseColor("rgba(255, 0, 0, 0.5)")).toBe(argb(128, 255, 0, 0));
      expect(parseColor("rgba(255, 0, 0, 0)")).toBe(argb(0, 255, 0, 0));
    });
  });

  describe("hsl() format", () => {
    it("parses hsl(h, s%, l%)", () => {
      // hsl(0, 100%, 50%) = red
      expect(parseColor("hsl(0, 100%, 50%)")).toBe(argb(255, 255, 0, 0));
      // hsl(120, 100%, 50%) = green
      expect(parseColor("hsl(120, 100%, 50%)")).toBe(argb(255, 0, 255, 0));
      // hsl(240, 100%, 50%) = blue
      expect(parseColor("hsl(240, 100%, 50%)")).toBe(argb(255, 0, 0, 255));
    });

    it("rejects invalid hsl", () => {
      expect(parseColor("hsl(0, 0, 0)")).toBeNull(); // missing %
    });
  });

  describe("hsla() format", () => {
    it("parses hsla with alpha", () => {
      expect(parseColor("hsla(0, 100%, 50%, 0.5)")).toBe(argb(128, 255, 0, 0));
    });
  });

  describe("non-color strings", () => {
    it("rejects plain text", () => {
      expect(parseColor("hello")).toBeNull();
      expect(parseColor("not a color")).toBeNull();
    });

    it("rejects numbers", () => {
      expect(parseColor("12345")).toBeNull();
      expect(parseColor("123")).toBeNull();
    });
  });
});

// ─── detectPasteType ──────────────────────────────────────────────────

describe("detectPasteType", () => {
  it("detects FAACBF as COLOR", () => {
    const result = detectPasteType("FAACBF");
    expect(result.pasteType).toBe(PasteTypeInt.COLOR);
    expect(result.pasteItem.type).toBe("color");
  });

  it("detects #FF0000 as COLOR", () => {
    const result = detectPasteType("#FF0000");
    expect(result.pasteType).toBe(PasteTypeInt.COLOR);
  });

  it("detects rgb(255,0,0) as COLOR", () => {
    const result = detectPasteType("rgb(255, 0, 0)");
    expect(result.pasteType).toBe(PasteTypeInt.COLOR);
  });

  it("detects hsl(0, 100%, 50%) as COLOR", () => {
    const result = detectPasteType("hsl(0, 100%, 50%)");
    expect(result.pasteType).toBe(PasteTypeInt.COLOR);
  });

  it("detects URLs as URL type", () => {
    const result = detectPasteType("https://example.com");
    expect(result.pasteType).toBe(PasteTypeInt.URL);
  });

  it("URL takes priority over color-like hex in URL", () => {
    const result = detectPasteType("https://example.com/FAACBF");
    expect(result.pasteType).toBe(PasteTypeInt.URL);
  });

  it("detects plain text as TEXT", () => {
    const result = detectPasteType("Hello world");
    expect(result.pasteType).toBe(PasteTypeInt.TEXT);
  });

  it("detects multiline text as TEXT, not color", () => {
    const result = detectPasteType("FAACBF\nsomething else");
    expect(result.pasteType).toBe(PasteTypeInt.TEXT);
  });

  it("handles whitespace around color", () => {
    const result = detectPasteType("  FAACBF  ");
    expect(result.pasteType).toBe(PasteTypeInt.COLOR);
  });

  it("handles whitespace around hex with #", () => {
    const result = detectPasteType("  #FF0000  ");
    expect(result.pasteType).toBe(PasteTypeInt.COLOR);
  });
});
