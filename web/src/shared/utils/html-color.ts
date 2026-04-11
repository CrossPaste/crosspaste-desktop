/**
 * Extract background color from HTML content.
 * Mirrors desktop HtmlColorUtils.getBackgroundColor() logic,
 * leveraging the browser's built-in CSS parser via getComputedStyle.
 */

const MAIN_CONTAINER_TAGS = new Set(["main", "article"]);
const MAIN_CONTAINER_IDS = new Set(["app", "root", "container", "wrapper", "main"]);
const MAIN_CONTAINER_CLASSES = new Set(["container", "wrapper", "main", "app", "root", "content"]);
const SKIP_TAGS = new Set(["script", "style", "noscript"]);

/**
 * Extract background color from HTML string.
 * Returns an ARGB integer (matching desktop format), or null if no background found.
 */
export function getHtmlBackgroundColor(html: string): number | null {
  const parser = new DOMParser();
  const doc = parser.parseFromString(html, "text/html");
  const body = doc.body;
  if (!body) return null;

  // 1. Check body itself
  const bodyColor = getColorFromElement(body);
  if (bodyColor !== null) return bodyColor;

  // 2. Check direct children (skip script/style/noscript)
  const children = Array.from(body.children).filter(
    (el) => !SKIP_TAGS.has(el.tagName.toLowerCase()),
  ) as HTMLElement[];

  if (children.length === 1) {
    const color = getColorFromElement(children[0]);
    if (color !== null) return color;
  } else if (children.length > 1) {
    const main = findMainContainer(children);
    if (main) {
      const color = getColorFromElement(main);
      if (color !== null) return color;
    }
  }

  // 3. Check html element (parent of body)
  const htmlEl = doc.documentElement;
  if (htmlEl) {
    const color = getColorFromElement(htmlEl);
    if (color !== null) return color;
  }

  return null;
}

function findMainContainer(elements: HTMLElement[]): HTMLElement | null {
  return (
    elements.find((el) => {
      const tag = el.tagName.toLowerCase();
      if (MAIN_CONTAINER_TAGS.has(tag)) return true;
      if (MAIN_CONTAINER_IDS.has(el.id)) return true;
      if (Array.from(el.classList).some((c) => MAIN_CONTAINER_CLASSES.has(c))) return true;
      if (tag === "div" && hasFullWidthStyle(el)) return true;
      return false;
    }) ??
    elements.find((el) => el.tagName.toLowerCase() === "div") ??
    null
  );
}

function hasFullWidthStyle(el: HTMLElement): boolean {
  const style = el.getAttribute("style") ?? "";
  return (
    /width:\s*100%/.test(style) ||
    /min-height:\s*100vh/.test(style) ||
    /height:\s*100vh/.test(style)
  );
}

function getColorFromElement(el: HTMLElement): number | null {
  // Check inline style
  const style = el.getAttribute("style") ?? "";
  if (style) {
    const color = parseBackgroundColorFromStyle(style);
    if (color !== null) return color;
  }

  // Check bgcolor attribute
  const bgcolor = el.getAttribute("bgcolor");
  if (bgcolor) {
    return parseCssColor(bgcolor);
  }

  return null;
}

function parseBackgroundColorFromStyle(style: string): number | null {
  // Use a temp element to leverage browser CSS parsing
  const temp = document.createElement("div");
  temp.style.cssText = style;

  // Prioritize background-color
  const bgColor = temp.style.backgroundColor;
  if (bgColor) return parseCssColor(bgColor);

  // Fall back to background shorthand — extract color part
  const bg = temp.style.background;
  if (bg) return parseCssColor(bg);

  return null;
}

/**
 * Parse a CSS color string to ARGB integer using canvas.
 * Returns null for transparent or unparseable values.
 */
function parseCssColor(colorStr: string): number | null {
  const trimmed = colorStr.trim().toLowerCase();
  if (!trimmed || trimmed === "transparent" || trimmed === "initial" || trimmed === "inherit") {
    return null;
  }

  // Use canvas to resolve any CSS color to RGBA
  const canvas = document.createElement("canvas");
  canvas.width = 1;
  canvas.height = 1;
  const ctx = canvas.getContext("2d");
  if (!ctx) return null;

  // Clear to transparent
  ctx.clearRect(0, 0, 1, 1);
  ctx.fillStyle = "#00000000";

  // Set the color — invalid colors will keep the previous fillStyle
  ctx.fillStyle = trimmed;

  // Check if the color was actually parsed (fillStyle is normalized)
  // If the color string was invalid, fillStyle might not change
  ctx.fillRect(0, 0, 1, 1);
  const [r, g, b, a] = ctx.getImageData(0, 0, 1, 1).data;

  // If fully transparent, treat as no background
  if (a === 0) return null;

  return ((a << 24) | (r << 16) | (g << 8) | b) | 0;
}

/**
 * Calculate relative luminance (WCAG 2.0).
 * Matches desktop ColorAccessibility.isDarkColor() threshold of 0.5.
 */
export function isDarkColor(argb: number): boolean {
  const r = ((argb >> 16) & 0xff) / 255;
  const g = ((argb >> 8) & 0xff) / 255;
  const b = (argb & 0xff) / 255;

  // sRGB to linear
  const rl = r <= 0.03928 ? r / 12.92 : Math.pow((r + 0.055) / 1.055, 2.4);
  const gl = g <= 0.03928 ? g / 12.92 : Math.pow((g + 0.055) / 1.055, 2.4);
  const bl = b <= 0.03928 ? b / 12.92 : Math.pow((b + 0.055) / 1.055, 2.4);

  const luminance = 0.2126 * rl + 0.7152 * gl + 0.0722 * bl;
  return luminance < 0.5;
}
