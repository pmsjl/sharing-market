import MarkdownIt from "markdown-it";
import type Token from "markdown-it/lib/token";

export interface PostPreviewModel {
  summary: string;
  thumbnail?: string;
  imageAlt: string;
}
const parser = new MarkdownIt({ html: true, linkify: false });
export const safePostImage = (value: string): string | undefined => {
  const src = value.trim();
  if (
    !src ||
    Array.from(src).some((char) => char.charCodeAt(0) <= 32 || char === "\\") ||
    src.startsWith("//") ||
    src.startsWith("#") ||
    src.startsWith("?")
  )
    return;
  if (/^[a-z][a-z\d+.-]*:/i.test(src)) {
    if (!/^https?:\/\//i.test(src)) return;
    try {
      const url = new URL(src);
      if (!url.hostname || url.username || url.password) return;
    } catch {
      return;
    }
  }
  return src;
};
export const buildPostPreview = (content?: string): PostPreviewModel => {
  const result: PostPreviewModel = { summary: "", imageAlt: "" };
  const parts: string[] = [];
  const walk = (tokens: Token[]) => {
    for (const token of tokens) {
      if (token.type === "image") {
        const src = safePostImage(token.attrGet("src") || "");
        if (!result.thumbnail && src) {
          result.thumbnail = src;
          result.imageAlt = token.content;
        }
      } else if (token.type === "text" || token.type === "code_inline")
        parts.push(token.content);
      else if (
        token.type === "softbreak" ||
        token.type === "hardbreak" ||
        (token.type.endsWith("_close") && token.block)
      )
        parts.push(" ");
      else if (token.children) walk(token.children);
    }
  };
  walk(parser.parse(content || "", {}));
  const text = parts.join("").replace(/\s+/g, " ").trim();
  result.summary = Array.from(text).slice(0, 160).join("");
  if (Array.from(text).length > 160)
    result.summary = Array.from(text).slice(0, 159).join("") + "…";
  return result;
};
