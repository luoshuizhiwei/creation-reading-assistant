/**
 * 视觉验收用：向隔离 profile 的创作工作区数据库写入场景正文（ELECTRON_RUN_AS_NODE 运行）。
 *
 * 计数列（han_count / punct_count / non_ws_count）直接复用 workspace 权威实现
 * countSceneBodyStats（electron/main/creation-workspace/scene-stats.ts），
 * 通过 esbuild 编译为 cjs 后在运行时加载，保证与正式保存路径口径完全一致。
 *
 * 用法：electron.exe visual-seed-body.cjs <profileDir> <repeatCount>
 */
const path = require("node:path");
const fs = require("node:fs");
const os = require("node:os");
const Database = require("better-sqlite3");
const { buildSync } = require("esbuild");

const profileDir = process.argv[2];
const repeat = Number(process.argv[3] ?? 3000);
if (!profileDir) {
  process.stderr.write("usage: visual-seed-body.cjs <profileDir> [repeatCount]\n");
  process.exitCode = 1;
  process.exit();
}

const root = path.resolve(__dirname, "..");
const compileDir = fs.mkdtempSync(path.join(os.tmpdir(), "creation-scene-stats-"));
const bundlePath = path.join(compileDir, "scene-stats.cjs");
buildSync({
  entryPoints: [path.join(root, "electron", "main", "creation-workspace", "scene-stats.ts")],
  outfile: bundlePath,
  bundle: true,
  platform: "node",
  format: "cjs",
  target: "node20"
});
const { countSceneBodyStats } = require(bundlePath);
fs.rmSync(compileDir, { recursive: true, force: true });

const databasePath = path.join(profileDir, "NovelWorkbench", "CreationWorkspace", "workspace.sqlite");
const db = new Database(databasePath);
const text = "测试正文。".repeat(repeat);
const bodyJson = JSON.stringify({
  type: "doc",
  content: [{ type: "paragraph", content: [{ type: "text", text }] }]
});
const stats = countSceneBodyStats(bodyJson);
const before = db.prepare("SELECT id, length(body_json) AS len, han_count, punct_count, non_ws_count FROM scenes").all();
const result = db
  .prepare("UPDATE scenes SET body_json = ?, han_count = ?, punct_count = ?, non_ws_count = ?, updated_at = ?")
  .run(bodyJson, stats.han, stats.punct, stats.nonWhitespace, new Date().toISOString());
const after = db.prepare("SELECT id, length(body_json) AS len, han_count, punct_count, non_ws_count FROM scenes").all();
db.close();
process.stdout.write(`${JSON.stringify({ changed: result.changes, stats, before, after })}\n`);
