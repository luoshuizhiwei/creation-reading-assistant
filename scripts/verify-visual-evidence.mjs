/**
 * 视觉证据完整性校验（不启动 GUI，只检查已有证据）。
 *
 * 校验项：
 * 1. evidence.json 存在且捕获矩阵与期望矩阵一致（2 尺寸 × 2 主题 × 3 fixture = 12 项，无重名、无缺项）。
 * 2. 每条捕获记录声明的 PNG 文件真实存在。
 * 3. 每条记录包含 fixture / theme / logicalViewport / screenshotPixels / deviceScaleFactor / overflowX。
 *    screenshotPixels 必须等于 logicalViewport × deviceScaleFactor（物理像素可以大于逻辑 viewport，这是正常的 DPI 缩放）。
 * 4. 每条记录的 overflowX 必须为 false。
 * 5. 主进程截图隔离钩子必须带 !app.isPackaged 守卫（打包环境不接受 profile 重定向）。
 */
import { existsSync, readFileSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(__dirname, "..");
const evidenceDir = path.join(__dirname, "visual-evidence");
const evidencePath = path.join(evidenceDir, "evidence.json");

function fail(message) {
  console.error(`[verify-visual-evidence] ${message}`);
  process.exit(1);
}

if (!existsSync(evidencePath)) {
  fail(`Missing ${evidencePath}. Run npm run visual:capture first.`);
}

const data = JSON.parse(readFileSync(evidencePath, "utf8"));
const expected = data.matrix ?? [];
const expectedCount = 12;
if (expected.length !== expectedCount) {
  fail(`Expected evidence matrix of ${expectedCount} entries, found ${expected.length}. Run npm run visual:capture.`);
}
if (new Set(expected).size !== expected.length) {
  fail("Evidence matrix contains duplicate file names.");
}

const captures = Array.isArray(data.captures) ? data.captures : [];
const seen = new Set();
for (const entry of captures) {
  if (!entry.file || typeof entry.file !== "string") fail(`Capture entry missing file: ${JSON.stringify(entry)}`);
  if (seen.has(entry.file)) fail(`Duplicate capture file: ${entry.file}`);
  seen.add(entry.file);

  const requiredFields = ["fixture", "theme", "logicalViewport", "screenshotPixels", "deviceScaleFactor", "overflowX"];
  for (const field of requiredFields) {
    if (!(field in entry)) fail(`Capture ${entry.file} missing field "${field}".`);
  }

  const filePath = path.join(evidenceDir, entry.file);
  if (!existsSync(filePath)) fail(`Declared screenshot missing on disk: ${entry.file}`);

  const { width: logicalWidth, height: logicalHeight } = entry.logicalViewport;
  const { width: pixelWidth, height: pixelHeight } = entry.screenshotPixels;
  const dpr = entry.deviceScaleFactor;
  const tolerance = 2;
  if (Math.abs(pixelWidth - logicalWidth * dpr) > tolerance || Math.abs(pixelHeight - logicalHeight * dpr) > tolerance) {
    fail(
      `Capture ${entry.file} pixel size ${pixelWidth}x${pixelHeight} does not match logical ${logicalWidth}x${logicalHeight} × dpr ${dpr}.`
    );
  }

  if (entry.overflowX !== false) {
    fail(`Capture ${entry.file} reports horizontal overflow (overflowX=${entry.overflowX}).`);
  }
}

for (const file of expected) {
  if (!seen.has(file)) fail(`Matrix entry missing from captures: ${file}`);
}

const mainSource = readFileSync(path.join(root, "electron", "main", "index.ts"), "utf8");
if (!mainSource.includes("!app.isPackaged ? process.env.CREATION_READER_CAPTURE_PROFILE : undefined")) {
  fail("Capture isolation hook must be gated by !app.isPackaged (packaged builds must never redirect userData).");
}

console.log(`[verify-visual-evidence] ${captures.length} evidence captures verified (matrix complete, files present, no overflow).`);
