import MarkdownIt from "markdown-it";
import type { MarkdownItConfigPlugin } from "md-editor-v3";

const isCjk = (char: string) =>
  /[\p{Script=Han}\p{Script=Hiragana}\p{Script=Katakana}\p{Script=Hangul}]/u.test(
    char
  );
const isPunctuation = (char: string) => /\p{P}/u.test(char);
const isCjkPunctuation = (char: string) =>
  /[\u3001-\u303f\uff01-\uff65]/u.test(char) && isPunctuation(char);
const isLetterOrNumber = (char: string) => /[\p{L}\p{N}]/u.test(char);

/** Relax only CJK punctuation boundaries for double asterisks. Standard delimiter
 * pairing still handles nesting and unmatched markers; code and escapes are
 * consumed by markdown-it before this rule. Never inject HTML into the source. */
export const cjkStrongPlugin = (md: MarkdownIt) => {
  md.inline.ruler.before("emphasis", "agent_cjk_strong", (state, silent) => {
    if (silent || state.src.slice(state.pos, state.pos + 2) !== "**")
      return false;
    const scanned = state.scanDelims(state.pos, true);
    if (scanned.length !== 2) return false;
    const before = state.pos > 0 ? state.src[state.pos - 1] : " ";
    const after = state.pos + 2 < state.posMax ? state.src[state.pos + 2] : " ";
    const open =
      scanned.can_open ||
      (isCjk(before) && isPunctuation(after)) ||
      (isLetterOrNumber(before) && isCjkPunctuation(after));
    const close =
      scanned.can_close ||
      (isPunctuation(before) && isCjk(after)) ||
      (isCjkPunctuation(before) && isLetterOrNumber(after));
    if (open === scanned.can_open && close === scanned.can_close) return false;
    for (let i = 0; i < 2; i++) {
      state.push("text", "", 0).content = "*";
      state.delimiters.push({
        marker: 0x2a,
        length: 2,
        jump: 0,
        token: state.tokens.length - 1,
        end: -1,
        open,
        close
      });
    }
    state.pos += 2;
    return true;
  });
};

/** md-editor-v3 passes this instance's editorId through its codeTabs plugin.
 * Scope compatibility to Agent answers; all built-in plugins stay installed. */
export const agentMarkdownPlugins = (
  plugins: MarkdownItConfigPlugin[]
): MarkdownItConfigPlugin[] => {
  const editorId = plugins.find((plugin) => plugin.type === "codeTabs")?.options
    ?.editorId;
  if (typeof editorId !== "string" || !editorId.startsWith("agent-answer-"))
    return plugins;
  return [
    ...plugins,
    { type: "agentCjkStrong", plugin: cjkStrongPlugin, options: {} }
  ];
};

const typingParser = new MarkdownIt({ html: true }).use(cjkStrongPlugin);
/** Render formatted blocks atomically so partial **, links and fences never
 * briefly appear as raw syntax. Plain prose retains the grapheme typewriter. */
export const splitMarkdownTypingUnits = (
  content: string,
  splitText: (text: string) => string[]
): string[] => {
  const normalized = content.replace(/\r\n?/g, "\n");
  const offsets = [0];
  for (let i = 0; i < normalized.length; i++)
    if (normalized[i] === "\n") offsets.push(i + 1);
  offsets.push(normalized.length);
  const protectedBlocks = new Set([
    "heading_open",
    "fence",
    "code_block",
    "html_block",
    "table_open",
    "bullet_list_open",
    "ordered_list_open",
    "blockquote_open",
    "hr"
  ]);
  const ranges = typingParser
    .parse(normalized, {})
    .filter(
      (token) =>
        token.map &&
        (protectedBlocks.has(token.type) ||
          (token.type === "inline" &&
            token.children?.some(
              (child) => !["text", "softbreak"].includes(child.type)
            )))
    )
    .map((token) => ({
      start: offsets[token.map![0]],
      end: offsets[token.map![1]]
    }))
    .sort((a, b) => a.start - b.start || b.end - a.end);
  const units: string[] = [];
  let cursor = 0;
  for (const range of ranges) {
    if (range.end <= cursor) continue;
    if (range.start > cursor)
      units.push(...splitText(normalized.slice(cursor, range.start)));
    units.push(normalized.slice(Math.max(cursor, range.start), range.end));
    cursor = range.end;
  }
  units.push(...splitText(normalized.slice(cursor)));
  return units;
};
