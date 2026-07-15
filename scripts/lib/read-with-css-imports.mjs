import { readFileSync } from "node:fs";
import path from "node:path";

export function readWithCssImports(filePath, seen = new Set()) {
  const normalized = path.normalize(filePath);
  if (seen.has(normalized)) return "";
  seen.add(normalized);

  const content = readFileSync(filePath, "utf-8");
  if (!filePath.endsWith(".css")) return content;

  const baseDir = path.dirname(filePath);
  return content.replace(/^@import\s+['"](.+?)['"];\s*$/gm, (_match, importPath) => {
    if (/^(?:https?:)?\/\//.test(importPath)) return "";
    return readWithCssImports(path.join(baseDir, importPath), seen);
  });
}
