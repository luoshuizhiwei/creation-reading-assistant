import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const repoRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const packageJson = JSON.parse(fs.readFileSync(path.join(repoRoot, "package.json"), "utf8"));
const version = `v${packageJson.version}`;
const artifactDir = path.resolve(repoRoot, "release-artifacts");

if (!artifactDir.startsWith(repoRoot + path.sep)) {
  throw new Error(`Refuse to clean a directory outside repo: ${artifactDir}`);
}

if (!fs.existsSync(artifactDir)) {
  fs.mkdirSync(artifactDir, { recursive: true });
  console.log(`[clean-local-release-artifacts] Created ${artifactDir}`);
  process.exit(0);
}

let removed = 0;
for (const entry of fs.readdirSync(artifactDir, { withFileTypes: true })) {
  const fullPath = path.join(artifactDir, entry.name);
  if (entry.isDirectory()) {
    fs.rmSync(fullPath, { recursive: true, force: true });
    removed += 1;
    continue;
  }
  if (!entry.name.includes(version)) {
    fs.rmSync(fullPath, { force: true });
    removed += 1;
  }
}

console.log(`[clean-local-release-artifacts] Kept local artifacts for ${version}; removed ${removed} old item(s).`);
