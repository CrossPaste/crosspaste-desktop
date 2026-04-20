import { describe, it, expect } from "vitest";
import { isSingleImgInBody } from "../plugin/remove-html-image-plugin";
import { RemoveHtmlImagePlugin } from "../plugin/remove-html-image-plugin";
import { PasteType, PasteTypeInt } from "@/shared/models/paste-item";
import type { HtmlPasteItem, ImagesPasteItem } from "@/shared/models/paste-item";
import type { TypedItem, PasteProcessContext } from "../plugin/paste-process-plugin";

// ─── isSingleImgInBody ───────────────────────────────────────────────

describe("isSingleImgInBody", () => {
  it("returns true for a bare <img> tag", () => {
    expect(isSingleImgInBody('<img src="x.png">')).toBe(true);
  });

  it("returns true for <img> wrapped in tags with no text", () => {
    expect(isSingleImgInBody("<html><body><img src='x.png'></body></html>")).toBe(true);
    expect(isSingleImgInBody("<div><p><img src='x.png' /></p></div>")).toBe(true);
  });

  it("returns false when there is visible text alongside <img>", () => {
    expect(isSingleImgInBody("<div>Hello<img src='x.png'></div>")).toBe(false);
    expect(isSingleImgInBody("<img src='x.png'>caption")).toBe(false);
  });

  it("returns false when there are multiple <img> tags", () => {
    expect(isSingleImgInBody("<img src='a.png'><img src='b.png'>")).toBe(false);
  });

  it("returns false when there are zero <img> tags", () => {
    expect(isSingleImgInBody("<div>no images here</div>")).toBe(false);
    expect(isSingleImgInBody("")).toBe(false);
  });

  it("ignores <img> inside HTML comments", () => {
    expect(isSingleImgInBody("<!-- <img src='x.png'> -->")).toBe(false);
    expect(isSingleImgInBody("<!-- <img src='a'> --><img src='b'>")).toBe(true);
  });

  it("ignores text inside HTML comments", () => {
    expect(isSingleImgInBody("<!-- some text --><img src='x.png'>")).toBe(true);
  });

  it("handles self-closing <img /> tags", () => {
    expect(isSingleImgInBody("<img />")).toBe(true);
    expect(isSingleImgInBody("<img/>")).toBe(true);
  });

  it("is case-insensitive for tag name", () => {
    expect(isSingleImgInBody("<IMG src='x.png'>")).toBe(true);
    expect(isSingleImgInBody("<Img src='x.png'>")).toBe(true);
  });

  it("treats whitespace-only text as empty", () => {
    expect(isSingleImgInBody("  <img src='x.png'>  ")).toBe(true);
    expect(isSingleImgInBody("\n\t<img src='x.png'>\n")).toBe(true);
  });
});

// ─── RemoveHtmlImagePlugin.process ───────────────────────────────────

describe("RemoveHtmlImagePlugin", () => {
  const plugin = new RemoveHtmlImagePlugin();
  const ctx: PasteProcessContext = { hashText: (s) => s };

  function htmlItem(html: string): TypedItem {
    const item: HtmlPasteItem = {
      type: PasteType.HTML,
      identifiers: [],
      hash: "",
      size: 0,
      html,
    };
    return { pasteType: PasteTypeInt.HTML, item };
  }

  const imagesItem: ImagesPasteItem = {
    type: PasteType.IMAGE,
    identifiers: [],
    hash: "",
    size: 0,
    count: 1,
    relativePathList: [],
    fileInfoTreeMap: {},
    dataUrl: "data:image/png;base64,AA==",
  };

  const imageItem: TypedItem = {
    pasteType: PasteTypeInt.IMAGE,
    item: imagesItem,
  };

  it("removes HTML item when it is a single-img wrapper and IMAGE is present", () => {
    const items = [htmlItem("<div><img src='x.png'></div>"), imageItem];
    const result = plugin.process(items, ctx);
    expect(result).toEqual([imageItem]);
  });

  it("keeps HTML item when it contains text alongside <img>", () => {
    const items = [htmlItem("<div>Hello<img src='x.png'></div>"), imageItem];
    const result = plugin.process(items, ctx);
    expect(result).toEqual(items);
  });

  it("keeps HTML item when no IMAGE item is present", () => {
    const items = [htmlItem("<img src='x.png'>")];
    const result = plugin.process(items, ctx);
    expect(result).toEqual(items);
  });

  it("does not remove HTML when <img> is inside a comment", () => {
    const items = [htmlItem("<!-- <img src='x.png'> -->"), imageItem];
    const result = plugin.process(items, ctx);
    expect(result).toEqual(items);
  });
});
