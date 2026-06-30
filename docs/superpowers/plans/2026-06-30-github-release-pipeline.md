# GitHub Release Pipeline Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Put the project under Git/GitHub and make every major update publishable with changelog notes plus downloadable Windows desktop and Android APK artifacts.

**Architecture:** Keep source code in Git, keep generated binaries out of Git, and let GitHub Actions build release assets from a tagged source snapshot. A `v*` tag triggers a Windows workflow that runs existing verification gates, builds the desktop unpacked app and Android debug APK, zips/copies artifacts, and uploads them to a GitHub Release.

**Tech Stack:** Git, GitHub private repository, GitHub Actions on `windows-latest`, Node.js 24, npm, Electron Builder, Capacitor Android, Gradle/JDK 17, `softprops/action-gh-release`.

---

### Task 1: Release documentation and ignore rules

**Files:**
- Create: `CHANGELOG.md`
- Create: `docs/GITHUB_RELEASE_PROCESS.md`
- Modify: `.gitignore`

- [ ] **Step 1: Add `CHANGELOG.md`**

Create a human-facing changelog with an `Unreleased` section and the current `0.1.0` baseline. The baseline should mention the desktop app, Android companion, LAN sync, QR pairing, and the current manual download artifacts.

- [ ] **Step 2: Add GitHub release process documentation**

Create `docs/GITHUB_RELEASE_PROCESS.md` with exact commands for first-time setup, normal major-update publishing, and where users download artifacts from GitHub Releases.

- [ ] **Step 3: Extend `.gitignore`**

Ignore `mobile-release/`, `.github-release/`, `.claude/`, and local generated archives so binary artifacts do not enter Git history.

- [ ] **Step 4: Review docs**

Run:

```powershell
rg -n "TODO|TBD|密钥|sk-" CHANGELOG.md docs/GITHUB_RELEASE_PROCESS.md .gitignore
```

Expected: no placeholders or secrets.

### Task 2: Release readiness guard

**Files:**
- Create: `scripts/verify-release-readiness.mjs`
- Modify: `package.json`

- [ ] **Step 1: Add a lightweight release readiness script**

The script should check that `.github/workflows/release.yml`, `CHANGELOG.md`, `docs/GITHUB_RELEASE_PROCESS.md`, `package-lock.json`, and `mobile/package-lock.json` exist, and that `.gitignore` ignores generated release artifacts.

- [ ] **Step 2: Add package script**

Add:

```json
"verify:release-readiness": "node scripts/verify-release-readiness.mjs"
```

- [ ] **Step 3: Run the guard**

Run:

```powershell
npm run verify:release-readiness
```

Expected: `[verify-release-readiness] GitHub release pipeline guards verified.`

### Task 3: GitHub Actions release workflow

**Files:**
- Create: `.github/workflows/release.yml`

- [ ] **Step 1: Add workflow trigger**

Trigger on `push` tags matching `v*` and on manual `workflow_dispatch`.

- [ ] **Step 2: Add build environment**

Use `windows-latest`, `actions/checkout`, `actions/setup-node` with Node 24, `actions/setup-java` with Temurin 17, root `npm ci`, and `npm ci --prefix mobile`.

- [ ] **Step 3: Add desktop validation and packaging**

Run:

```powershell
npm run verify:release-readiness
npm run verify:beta
npm run dist:beta:offline
npm run verify:beta:release
```

Then compress `release-beta/win-unpacked` to `release-artifacts/creation-reading-assistant-windows-win-unpacked.zip`.

- [ ] **Step 4: Add Android validation and APK packaging**

Run:

```powershell
npm run mobile:build
npx cap sync android
.\gradlew.bat assembleDebug
```

Copy `mobile/android/app/build/outputs/apk/debug/app-debug.apk` to `release-artifacts/creation-reading-assistant-mobile-debug.apk`.

- [ ] **Step 5: Add checksums and artifact upload**

Generate SHA256 checksums for all release artifacts and upload them with `actions/upload-artifact`.

- [ ] **Step 6: Add GitHub Release publishing**

Use `softprops/action-gh-release@v2` only when `github.ref` is a tag. Upload the Windows zip, APK, and checksum file. Use `CHANGELOG.md` as release body for the first iteration.

### Task 4: Initialize Git and first local commit

**Files:** all intended source/config/docs files, excluding ignored generated artifacts.

- [ ] **Step 1: Initialize repository**

Run:

```powershell
git init
git status -sb
```

- [ ] **Step 2: Stage intended files**

Run:

```powershell
git add .
git status -sb
```

Confirm generated folders such as `node_modules/`, `out/`, `release-beta/`, and `mobile-release/` are not staged.

- [ ] **Step 3: Commit baseline**

Run:

```powershell
git commit -m "chore: prepare GitHub release pipeline"
```

### Task 5: GitHub private repo and first release

**Files:** Git remote configuration and GitHub Release.

- [ ] **Step 1: Install or configure GitHub CLI**

If `gh` is missing, install it with winget or ask the user to install GitHub CLI. Then run:

```powershell
gh auth login
gh auth status
```

- [ ] **Step 2: Create private GitHub repository**

Run:

```powershell
gh repo create creation-reading-assistant --private --source . --remote origin --push
```

- [ ] **Step 3: Create first tag**

Run:

```powershell
git tag -a v0.1.0 -m "v0.1.0"
git push origin v0.1.0
```

- [ ] **Step 4: Confirm Release artifacts**

After GitHub Actions completes, check the Release page contains:

- `creation-reading-assistant-windows-win-unpacked.zip`
- `creation-reading-assistant-mobile-debug.apk`
- `SHA256SUMS.txt`

If the workflow fails, inspect Actions logs and fix the smallest failing step.
