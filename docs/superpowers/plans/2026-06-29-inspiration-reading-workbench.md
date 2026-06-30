# Inspiration Reading Workbench Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Reposition the app into a local-first inspiration, AI polishing, and reading-time workbench, and remove the legacy novel project workbench from the renderer/API surface.

**Architecture:** Keep all privileged file IO, AI HTTP requests, and encrypted API key storage in Electron main. Renderer uses typed preload APIs, Zustand stores, and existing paper-ink UI primitives. Existing library/reader/stats paths remain intact.

**Tech Stack:** Electron main/preload IPC, React 18, Zustand, TypeScript, local JSON stores with atomic writes, Electron safeStorage, OpenAI-compatible chat completions.

---

## Tasks

### Task 1: Verification and documentation

- [ ] Add formal design doc and implementation plan under `docs/superpowers`.
- [ ] Add `verify:reposition`, `verify:inspiration`, and `verify:ai-settings` scripts.
- [ ] Run each verifier before implementation and confirm it fails because the new surface is missing.

### Task 2: Shared types and APIs

- [ ] Add `src/types/inspiration.ts` and `src/types/ai.ts`.
- [ ] Extend `AppSettings` with `ai`.
- [ ] Extend `DesktopApi` and preload with `inspiration` and `ai` namespaces.

### Task 3: Main-process storage and AI

- [ ] Add `inspirations.json` helpers and IPC handlers.
- [ ] Add AI settings normalization, encrypted API key persistence, OpenAI-compatible request execution, and AI IPC handlers.
- [ ] Extend global search to return inspiration results.

### Task 4: Renderer pages and stores

- [x] Add inspiration service, store, hook, and `InspirationPage`.
- [x] Add AI service and initial `AiPage`; standalone `AiPage` was later removed when AI was folded into the inspiration center.
- [x] Rework `StartPage` as the new dashboard.
- [x] Update `AppScreen` and route rendering so legacy workbench is no longer reachable.
- [x] Add AI controls to Settings.

### Task 4.5: Legacy writing cleanup

- [x] Remove `WorkbenchPage`, `src/features/project`, `src/features/export`, old project/chapter/card/export hooks, services, utilities, and types.
- [x] Remove old project/chapter/card/export namespaces from `DesktopApi` and preload.
- [x] Remove old project/chapter/card/export IPC registrations from Electron main.
- [x] Move shared `ID`/`ISODateString` to `src/types/common.ts` and delete `src/types/project.ts`.
- [x] Narrow backup/restore and debug export to app-data only.
- [x] Add and pass `npm run verify:clean-reposition`.

### Task 5: Reading-to-inspiration link

- [x] Add “记为灵感” actions to TXT/Markdown and EPUB reader pages.
- [x] Save book/source/progress context on created inspiration.

### Task 6: Verification and closeout

- [x] Run `npm run verify:reposition`.
- [x] Run `npm run verify:inspiration`.
- [x] Run `npm run verify:ai-settings`.
- [x] Run `npm run verify:ux-polish`.
- [x] Run `npm run build`.
- [x] Run `npm run verify:beta`.
- [ ] Update Obsidian Codex project/open-loop/skill usage records.
