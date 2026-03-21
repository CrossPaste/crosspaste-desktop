/**
 * PasteType constants matching Kotlin sealed class @SerialName values.
 * These are the class discriminator values used by kotlinx.serialization.
 */
export const PasteType = {
  TEXT: "text",
  URL: "url",
  HTML: "html",
  FILE: "files",
  IMAGE: "images",
  RTF: "rtf",
  COLOR: "color",
} as const;

export type PasteTypeValue = (typeof PasteType)[keyof typeof PasteType];

/**
 * Integer type codes from PasteData.pasteType (matches PasteType.kt type field).
 * Used for PasteData-level type identification.
 */
export const PasteTypeInt = {
  TEXT: 0,
  URL: 1,
  HTML: 2,
  FILE: 3,
  IMAGE: 4,
  RTF: 5,
  COLOR: 6,
} as const;

/** Map integer pasteType to string PasteType */
export const PASTE_TYPE_FROM_INT: Record<number, PasteTypeValue> = {
  [PasteTypeInt.TEXT]: PasteType.TEXT,
  [PasteTypeInt.URL]: PasteType.URL,
  [PasteTypeInt.HTML]: PasteType.HTML,
  [PasteTypeInt.FILE]: PasteType.FILE,
  [PasteTypeInt.IMAGE]: PasteType.IMAGE,
  [PasteTypeInt.RTF]: PasteType.RTF,
  [PasteTypeInt.COLOR]: PasteType.COLOR,
};

export const PASTE_TYPE_LABELS: Record<PasteTypeValue, string> = {
  [PasteType.TEXT]: "Text",
  [PasteType.URL]: "Link",
  [PasteType.HTML]: "HTML",
  [PasteType.FILE]: "File",
  [PasteType.IMAGE]: "Image",
  [PasteType.RTF]: "RTF",
  [PasteType.COLOR]: "Color",
};

export const PASTE_TYPE_COLORS: Record<PasteTypeValue, string> = {
  [PasteType.TEXT]: "bg-blue-100 text-blue-700 dark:bg-blue-900 dark:text-blue-300",
  [PasteType.URL]: "bg-green-100 text-green-700 dark:bg-green-900 dark:text-green-300",
  [PasteType.HTML]: "bg-orange-100 text-orange-700 dark:bg-orange-900 dark:text-orange-300",
  [PasteType.FILE]: "bg-purple-100 text-purple-700 dark:bg-purple-900 dark:text-purple-300",
  [PasteType.IMAGE]: "bg-pink-100 text-pink-700 dark:bg-pink-900 dark:text-pink-300",
  [PasteType.RTF]: "bg-yellow-100 text-yellow-700 dark:bg-yellow-900 dark:text-yellow-300",
  [PasteType.COLOR]: "bg-cyan-100 text-cyan-700 dark:bg-cyan-900 dark:text-cyan-300",
};

/** Base fields present on all PasteItem types */
interface PasteItemBase {
  type: PasteTypeValue;
  identifiers: string[];
  hash: string;
  size: number;
  extraInfo?: unknown;
}

export interface TextPasteItem extends PasteItemBase {
  type: typeof PasteType.TEXT;
  text: string;
}

export interface UrlPasteItem extends PasteItemBase {
  type: typeof PasteType.URL;
  url: string;
}

export interface HtmlPasteItem extends PasteItemBase {
  type: typeof PasteType.HTML;
  html: string;
}

export interface FilesPasteItem extends PasteItemBase {
  type: typeof PasteType.FILE;
  count: number;
  basePath?: string;
  relativePathList: string[];
  fileInfoTreeMap: Record<string, unknown>;
}

export interface ImagesPasteItem extends PasteItemBase {
  type: typeof PasteType.IMAGE;
  count: number;
  basePath?: string;
  relativePathList: string[];
  fileInfoTreeMap: Record<string, unknown>;
  /** Inline image data URL for locally captured clipboard images */
  dataUrl?: string;
}

export interface RtfPasteItem extends PasteItemBase {
  type: typeof PasteType.RTF;
  rtf: string;
}

export interface ColorPasteItem extends PasteItemBase {
  type: typeof PasteType.COLOR;
  color: number; // 32-bit ARGB integer
}

export type PasteItem =
  | TextPasteItem
  | UrlPasteItem
  | HtmlPasteItem
  | FilesPasteItem
  | ImagesPasteItem
  | RtfPasteItem
  | ColorPasteItem;
