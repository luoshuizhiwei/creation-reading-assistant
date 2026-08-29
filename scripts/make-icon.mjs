/**
 * 生成应用图标 build/icon.ico（品牌来源：desktop-brand-mark —— 朱砂渐变圆角方块 + 白色衬线「阅」）。
 * 用 Electron 离屏窗口的 canvas 绘制（需要系统字体渲染汉字），1024 母版缩到各尺寸后按 ICO 规范打包：
 * 小尺寸（16–64）内嵌 32bpp DIB（Windows 经典格式），128/256 内嵌 PNG。
 * 用法：node scripts/make-icon.mjs
 */
import { mkdtempSync, mkdirSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { _electron as electron } from "playwright-core";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(__dirname, "..");
const outDir = path.join(root, "build");
const electronPath = path.join(root, "node_modules", "electron", "dist", "electron.exe");
const profileDir = mkdtempSync(path.join(tmpdir(), "creation-icon-"));

const SIZES = [16, 24, 32, 48, 64, 128, 256];
const MASTER = 1024;

const app = await electron.launch({
  executablePath: electronPath,
  args: ["--headless"],
  env: { ...process.env, ELECTRON_RUN_AS_NODE: undefined },
  chromiumSandbox: false
});
const page = await app.firstWindow();
await page.waitForLoadState("domcontentloaded");

// 在新文档里画图并取回像素（应用窗口 CSP 带 Trusted Types，需用 data: URL 开新文档绕开）。
const drawHtml = `data:text/html,${encodeURIComponent(`<!doctype html><html><body><canvas id="c" width="${MASTER}" height="${MASTER}"></canvas></body></html>`)}`;
await page.goto(drawHtml);

const result = await page.evaluate(
  async ({ master, sizes }) => {
    const canvas = document.getElementById("c");
    const ctx = canvas.getContext("2d");

    const drawAt = (px) => {
      const w = px;
      const c = document.createElement("canvas");
      c.width = w;
      c.height = w;
      const g = c.getContext("2d");
      const scale = w / 256; // 设计坐标以 256 为基准

      // 朱砂渐变圆角方块（模拟 160deg linear-gradient: 82% seal+#fff → seal）
      const radius = 58 * scale; // 58/256 ≈ 0.23 圆角率，与 desktop-brand-mark 一致
      const grad = g.createLinearGradient(w * 0.16, 0, w * 0.84, w);
      grad.addColorStop(0, "#d8513d");
      grad.addColorStop(1, "#bc3f2b");
      g.beginPath();
      g.roundRect(w * 0.045, w * 0.045, w * 0.91, w * 0.91, radius);
      g.fillStyle = grad;
      g.fill();

      // 内侧白环（对应 inset 0 0 0 2px rgba(255,255,255,.28)）
      g.beginPath();
      g.roundRect(w * 0.045 + 3 * scale, w * 0.045 + 3 * scale, w * 0.91 - 6 * scale, w * 0.91 - 6 * scale, radius - 3 * scale);
      g.strokeStyle = "rgba(255,255,255,0.30)";
      g.lineWidth = Math.max(1, 2.2 * scale);
      g.stroke();

      // 白色衬线「阅」
      g.fillStyle = "#ffffff";
      g.font = `700 ${Math.round(w * 0.56)}px "Noto Serif SC","Source Han Serif SC",SimSun,serif`;
      g.textAlign = "center";
      g.textBaseline = "middle";
      g.shadowColor = "rgba(0,0,0,0.22)";
      g.shadowBlur = 3 * scale;
      g.shadowOffsetY = 1.2 * scale;
      g.fillText("阅", w / 2, w / 2 + w * 0.015);
      return c;
    };

    const toBase64 = (bytes) => {
      let bin = "";
      const chunk = 0x8000;
      for (let i = 0; i < bytes.length; i += chunk) {
        bin += String.fromCharCode(...bytes.subarray(i, i + chunk));
      }
      return btoa(bin);
    };

    // 母版：1024 绘制再放大缩小时先画大图保证边缘质量
    const masterCanvas = drawAt(master);

    const images = [];
    for (const size of sizes) {
      const c = document.createElement("canvas");
      c.width = size;
      c.height = size;
      const g = c.getContext("2d");
      g.imageSmoothingEnabled = true;
      g.imageSmoothingQuality = "high";
      g.drawImage(masterCanvas, 0, 0, size, size);
      if (size >= 128) {
        images.push({ size, png: c.toDataURL("image/png").split(",")[1] });
      } else {
        const data = g.getImageData(0, 0, size, size).data;
        images.push({ size, rgba: toBase64(data) });
      }
    }
    return images;
  },
  { master: MASTER, sizes: SIZES }
);

await app.close();
rmSync(profileDir, { recursive: true, force: true });

// ---------- ICO 打包 ----------
const dibFor = (size, rgbaB64) => {
  const rgba = Buffer.from(rgbaB64, "base64");
  const header = Buffer.alloc(40);
  header.writeUInt32LE(40, 0); // biSize
  header.writeInt32LE(size, 4); // biWidth
  header.writeInt32LE(size * 2, 8); // biHeight（XOR + AND 两层）
  header.writeUInt16LE(1, 12); // biPlanes
  header.writeUInt16LE(32, 14); // biBitCount
  header.writeUInt32LE(0, 16); // BI_RGB
  const andRow = Math.ceil(size / 32) * 4;
  const andSize = andRow * size;
  header.writeUInt32LE(size * size * 4 + andSize, 20); // biSizeImage
  // 像素：自底向上 BGRA
  const xor = Buffer.alloc(size * size * 4);
  for (let y = 0; y < size; y += 1) {
    const srcRow = (size - 1 - y) * size * 4;
    for (let x = 0; x < size; x += 1) {
      const s = srcRow + x * 4;
      const d = (y * size + x) * 4;
      xor[d] = rgba[s + 2];
      xor[d + 1] = rgba[s + 1];
      xor[d + 2] = rgba[s];
      xor[d + 3] = rgba[s + 3];
    }
  }
  const andMask = Buffer.alloc(andSize); // 全 0：不透明度交给 alpha 通道
  return Buffer.concat([header, xor, andMask]);
};

const entries = result
  .map((img) => {
    if (img.png) {
      return { size: img.size, data: Buffer.from(img.png, "base64"), isPng: true };
    }
    return { size: img.size, data: dibFor(img.size, img.rgba), isPng: false };
  })
  .sort((a, b) => b.size - a.size);

const header = Buffer.alloc(6);
header.writeUInt16LE(0, 0);
header.writeUInt16LE(1, 2); // type: icon
header.writeUInt16LE(entries.length, 4);

// ICO 布局：目录区（全部 16 字节项）必须连续，之后才是各图像数据区。
const dirSize = 6 + entries.length * 16;
const dirEntries = [];
const dataBlobs = [];
let offset = dirSize;
for (const entry of entries) {
  const dir = Buffer.alloc(16);
  dir.writeUInt8(entry.size >= 256 ? 0 : entry.size, 0);
  dir.writeUInt8(entry.size >= 256 ? 0 : entry.size, 1);
  dir.writeUInt8(0, 2); // colors
  dir.writeUInt8(0, 3); // reserved
  dir.writeUInt16LE(1, 4); // planes
  dir.writeUInt16LE(32, 6); // bitcount
  dir.writeUInt32LE(entry.data.length, 8);
  dir.writeUInt32LE(offset, 12);
  dirEntries.push(dir);
  dataBlobs.push(entry.data);
  offset += entry.data.length;
}

mkdirSync(outDir, { recursive: true });
const ico = Buffer.concat([header, ...dirEntries, ...dataBlobs]);
writeFileSync(path.join(outDir, "icon.ico"), ico);

// 附带 256 PNG 母版，供 README/发布页使用
const png256 = entries.find((e) => e.size === 256 && e.isPng);
if (png256) writeFileSync(path.join(outDir, "icon-256.png"), png256.data);

console.log(`[make-icon] wrote build/icon.ico (${ico.length} bytes, ${entries.length} sizes: ${entries.map((e) => e.size).join("/")})`);
