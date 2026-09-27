import { readFileSync, readdirSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(__dirname, "..", "..");
const workspaceDir = path.join(root, "electron", "main", "creation-workspace");

/**
 * 创作工作区实现模块清单。
 *
 * 守卫脚本要校验的是「工作区必须具备某项实现」，而不是「某段代码必须写在 index.ts 里」。
 * 工作区入口已按职责拆分为多个域模块（纯移动式重构），因此断言必须覆盖整个模块集合，
 * 否则任何一次正常拆分都会让守卫误报。新增域模块无需再改守卫。
 *
 * 约定：`*-contract.ts` 是运行期契约入口、`*.d.ts` 是类型声明、`test-utils.ts` 是测试夹具，
 * 它们不属于生产实现，故排除在文本断言范围之外。
 */
export function listCreationWorkspaceFiles() {
  return readdirSync(workspaceDir)
    .filter((name) => name.endsWith(".ts"))
    .filter((name) => !name.endsWith("-contract.ts"))
    .filter((name) => !name.endsWith(".d.ts"))
    .filter((name) => name !== "test-utils.ts")
    .sort()
    .map((name) => path.join("electron", "main", "creation-workspace", name).split(path.sep).join("/"));
}

export function readCreationWorkspace() {
  return listCreationWorkspaceFiles()
    .map((file) => readFileSync(path.join(root, file), "utf8"))
    .join("\n");
}
