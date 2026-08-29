/** AI 上下文包的量化口径（字符数 / token 估算），供构建器与确认对话框共用。 */

/** 非空白字符数（与写作台口径一致）。 */
export function countSignificantChars(text: string): number {
  return text.replace(/\s/g, "").length;
}

/** CJK 文本粗略 token 估算：约 1.6 字符/token，仅作量级参考。 */
export function estimateTokens(chars: number): number {
  return Math.ceil(chars / 1.6);
}

interface PackLike {
  groups: Array<{ id?: unknown; chars: number; estTokens: number }>;
}

/** 按排除集合汇总待发送字符数与 token 估算；pack 为空（单内容模式）返回 0。 */
export function estimatePackTokens(
  pack: PackLike | undefined,
  excluded: ReadonlySet<string>
): { chars: number; tokens: number } {
  if (!pack) return { chars: 0, tokens: 0 };
  return pack.groups
    .filter((group) => !excluded.has(String(group.id)))
    .reduce(
      (sum, group) => ({ chars: sum.chars + group.chars, tokens: sum.tokens + group.estTokens }),
      { chars: 0, tokens: 0 }
    );
}
