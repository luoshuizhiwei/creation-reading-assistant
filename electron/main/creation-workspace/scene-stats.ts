import type { CreationDocument } from "../../../src/types/creation";

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

/**
 * 场景正文三口径统计（权威实现，视觉 seed 与 workspace 共用同一真源）：
 * - han：汉字数（\p{Script=Han}）
 * - punct：标点/符号/emoji 数（非空白字符中命中 \p{P} 或 \p{S}）
 * - nonWhitespace：非空白字符总数
 * 按 Unicode code point 计数，仅排除空白（\s）；汉字、拉丁字符、标点、符号与 emoji 均计入。
 */
export function countSceneBodyStats(bodyJson: string): { han: number; punct: number; nonWhitespace: number } {
  let document: CreationDocument;
  try {
    document = JSON.parse(bodyJson) as CreationDocument;
  } catch {
    return { han: 0, punct: 0, nonWhitespace: 0 };
  }
  let han = 0;
  let punct = 0;
  let nonWhitespace = 0;
  const collect = (nodes: unknown[]): void => {
    for (const node of nodes) {
      if (!isRecord(node)) continue;
      if (node.type === "text" && typeof node.text === "string") {
        for (const character of node.text) {
          if (/\p{Script=Han}/u.test(character)) han += 1;
          if (!/\s/u.test(character)) {
            nonWhitespace += 1;
            if (/[\p{P}\p{S}]/u.test(character)) punct += 1;
          }
        }
      } else if (Array.isArray(node.content)) collect(node.content);
    }
  };
  collect(document.content ?? []);
  return { han, punct, nonWhitespace };
}
