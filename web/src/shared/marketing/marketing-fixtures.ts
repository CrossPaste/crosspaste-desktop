import type { PasteData, PasteCollection } from "@/shared/models/paste-data";
import { PasteState } from "@/shared/models/paste-data";
import {
  PasteType,
  PasteTypeInt,
  type ColorPasteItem,
  type FilesPasteItem,
  type HtmlPasteItem,
  type ImagesPasteItem,
  type TextPasteItem,
  type UrlPasteItem,
} from "@/shared/models/paste-item";

const MAC_INSTANCE = "macos-marketing";
const LANGUAGE_STORAGE_KEY = "ui_language";

const emptyCollection: PasteCollection = { pasteItems: [] };

function normalizeLang(raw: string | undefined): "zh" | "en" {
  return (raw ?? "").toLowerCase().startsWith("zh") ? "zh" : "en";
}

async function resolveLang(): Promise<"zh" | "en"> {
  try {
    const stored = await chrome.storage.local.get(LANGUAGE_STORAGE_KEY);
    const persisted = stored[LANGUAGE_STORAGE_KEY] as string | undefined;
    if (persisted) return normalizeLang(persisted);
  } catch {
    /* fall through to navigator */
  }
  return normalizeLang(navigator.language);
}

async function loadText(path: string): Promise<string> {
  try {
    const res = await fetch(path);
    if (!res.ok) return "";
    return await res.text();
  } catch {
    return "";
  }
}

function colorFixture(): PasteData {
  // #A6D6D6FF parsed as #RRGGBBAA → ARGB int32 (signed)
  const argb = (0xff << 24) | (0xa6 << 16) | (0xd6 << 8) | 0xd6;
  const item: ColorPasteItem = {
    type: PasteType.COLOR,
    identifiers: ["marketing-color"],
    hash: "marketing-color",
    size: 4,
    color: argb,
  };
  return {
    _id: 6,
    id: 6,
    appInstanceId: MAC_INSTANCE,
    favorite: false,
    pasteAppearItem: item,
    pasteCollection: emptyCollection,
    pasteType: PasteTypeInt.COLOR,
    source: "Figma",
    size: item.size,
    hash: item.hash,
    pasteState: PasteState.LOADED,
  };
}

function urlFixture(): PasteData {
  const url = "https://github.com";
  const item: UrlPasteItem = {
    type: PasteType.URL,
    identifiers: ["marketing-url"],
    hash: "marketing-url",
    size: url.length,
    url,
  };
  return {
    _id: 5,
    id: 5,
    appInstanceId: MAC_INSTANCE,
    favorite: false,
    pasteAppearItem: item,
    pasteCollection: emptyCollection,
    pasteType: PasteTypeInt.URL,
    source: "Google Chrome",
    size: item.size,
    hash: item.hash,
    pasteState: PasteState.LOADED,
  };
}

function fileFixture(): PasteData {
  const fileName = "data.zip";
  const item: FilesPasteItem = {
    type: PasteType.FILE,
    identifiers: ["marketing-file"],
    hash: "marketing-file",
    size: 1024 * 64,
    count: 1,
    relativePathList: [fileName],
    fileInfoTreeMap: {},
  };
  return {
    _id: 4,
    id: 4,
    appInstanceId: MAC_INSTANCE,
    favorite: false,
    pasteAppearItem: item,
    pasteCollection: emptyCollection,
    pasteType: PasteTypeInt.FILE,
    source: "Finder",
    size: item.size,
    hash: item.hash,
    pasteState: PasteState.LOADED,
  };
}

function imageFixture(): PasteData {
  const fileName = "sunflower.png";
  const item: ImagesPasteItem = {
    type: PasteType.IMAGE,
    identifiers: ["marketing-image"],
    hash: "marketing-image",
    size: 1024 * 200,
    count: 1,
    relativePathList: [fileName],
    fileInfoTreeMap: {},
    dataUrl: `/marketing/${fileName}`,
  };
  return {
    _id: 3,
    id: 3,
    appInstanceId: MAC_INSTANCE,
    favorite: true,
    pasteAppearItem: item,
    pasteCollection: emptyCollection,
    pasteType: PasteTypeInt.IMAGE,
    source: "Photo Album",
    size: item.size,
    hash: item.hash,
    pasteState: PasteState.LOADED,
  };
}

function textFixture(text: string): PasteData {
  const item: TextPasteItem = {
    type: PasteType.TEXT,
    identifiers: ["marketing-text"],
    hash: "marketing-text",
    size: text.length,
    text,
  };
  return {
    _id: 2,
    id: 2,
    appInstanceId: MAC_INSTANCE,
    favorite: false,
    pasteAppearItem: item,
    pasteCollection: emptyCollection,
    pasteType: PasteTypeInt.TEXT,
    source: "Notes Archive",
    size: item.size,
    hash: item.hash,
    pasteState: PasteState.LOADED,
  };
}

function htmlFixture(html: string): PasteData {
  const item: HtmlPasteItem = {
    type: PasteType.HTML,
    identifiers: ["marketing-html"],
    hash: "marketing-html",
    size: html.length,
    html,
    extraInfo: { background: 0xffffffff | 0 },
  };
  return {
    _id: 1,
    id: 1,
    appInstanceId: MAC_INSTANCE,
    favorite: false,
    pasteAppearItem: item,
    pasteCollection: emptyCollection,
    pasteType: PasteTypeInt.HTML,
    source: "Email",
    size: item.size,
    hash: item.hash,
    pasteState: PasteState.LOADED,
  };
}

const cachedByLang = new Map<string, Promise<PasteData[]>>();

export function getMarketingPastes(): Promise<PasteData[]> {
  return resolveLang().then((lang) => {
    const existing = cachedByLang.get(lang);
    if (existing) return existing;
    const promise = (async () => {
      const [text, html] = await Promise.all([
        loadText(`/marketing/${lang}.txt`),
        loadText(`/marketing/${lang}.html`),
      ]);
      return [
        htmlFixture(html || "<p>HTML preview</p>"),
        textFixture(text || "Marketing text"),
        imageFixture(),
        fileFixture(),
        urlFixture(),
        colorFixture(),
      ];
    })();
    cachedByLang.set(lang, promise);
    return promise;
  });
}

export function invalidateMarketingCache(): void {
  cachedByLang.clear();
}
