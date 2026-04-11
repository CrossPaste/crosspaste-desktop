/**
 * Convert a 32-bit ARGB integer to color components.
 * Java/Kotlin uses signed 32-bit integers for colors.
 */
export function argbToComponents(argb: number): {
  a: number;
  r: number;
  g: number;
  b: number;
} {
  return {
    a: (argb >>> 24) & 0xff,
    r: (argb >>> 16) & 0xff,
    g: (argb >>> 8) & 0xff,
    b: argb & 0xff,
  };
}

export function argbToHex(argb: number): string {
  const { r, g, b } = argbToComponents(argb);
  return `#${r.toString(16).padStart(2, "0")}${g.toString(16).padStart(2, "0")}${b.toString(16).padStart(2, "0")}`.toUpperCase();
}

export function argbToRgba(argb: number): string {
  const { a, r, g, b } = argbToComponents(argb);
  return `rgba(${r}, ${g}, ${b}, ${(a / 255).toFixed(2)})`;
}
