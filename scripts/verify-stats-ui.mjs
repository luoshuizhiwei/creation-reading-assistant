import { readFileSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(__dirname, "..");
const statsPage = readFileSync(path.join(root, "src/features/library/ReadingStatsPage.tsx"), "utf8");

const requiredSnippets = [
  "todayDurationMs",
  "last7DaysDurationMs",
  "last30DaysDurationMs",
  "totalDurationMs",
  "byBook",
  "recentBooks",
  "recentSessions",
  "averageSessionDurationMs",
  "基于有效阅读会话聚合",
  "stats-rhythm-chart",
  "stats-rhythm-bar"
];

const missing = requiredSnippets.filter((snippet) => !statsPage.includes(snippet));
if (missing.length > 0) {
  console.error("[verify-stats-ui] Missing expected stats UI bindings:");
  for (const item of missing) console.error(`  - ${item}`);
  process.exit(1);
}

if (/stats\.daily\.slice\(-30\)[\s\S]*className=\"[^\"]*flex-1/.test(statsPage)) {
  console.error("[verify-stats-ui] Daily rhythm bars must not use flex-1; a single day should not stretch into a full-width block.");
  process.exit(1);
}

console.log("[verify-stats-ui] Reading stats UI bindings verified.");
