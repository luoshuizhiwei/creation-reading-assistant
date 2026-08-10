// Storage adapter spike: minimal CreationWorkspace schema on SQLite.
// Intended to be run directly by Electron 33 (main process), e.g.:
//   node_modules/electron/dist/electron.exe scripts/spikes/creation-sqlite-spike.cjs
// Outputs a single line of JSON on stdout when every check passes.
// Always cleans up its temporary directory and quits, success or failure.
"use strict";

const { app } = require("electron");
const fs = require("node:fs");
const os = require("node:os");
const path = require("node:path");

const SCENE_COUNT = 2000;
const CARD_COUNT = 10000;
const RELATION_COUNT = 20000;
const KINDS = ["character", "faction", "location", "item", "world", "plot"];
const TYPES = ["mentions", "opposes", "allies", "owns", "located_in", "knows"];

function pad4(n) {
  return String(n).padStart(4, "0");
}

app.whenReady().then(() => {
  const startedAt = Date.now();
  const phases = {};
  let tmpDir = null;
  let db = null;
  let ok = false;
  let jsonLine = "";

  function cleanup() {
    try {
      if (db) db.close();
    } catch (_) {
      /* ignore */
    }
    try {
      if (tmpDir) fs.rmSync(tmpDir, { recursive: true, force: true });
    } catch (_) {
      /* ignore */
    }
  }

  try {
    const Database = require("better-sqlite3");

    const openStart = Date.now();
    tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), "creation-sqlite-spike-"));
    db = new Database(path.join(tmpDir, "spike.db"));
    db.pragma("journal_mode = WAL");
    db.pragma("foreign_keys = ON");
    const journalMode = db.pragma("journal_mode", { simple: true });
    const foreignKeysEnabled = db.pragma("foreign_keys", { simple: true }) === 1;
    phases.openMs = Date.now() - openStart;

    const schemaStart = Date.now();
    db.exec(`
      CREATE TABLE projects (
        id INTEGER PRIMARY KEY,
        title TEXT NOT NULL,
        created_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ','now')),
        updated_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ','now'))
      );

      CREATE TABLE scenes (
        id INTEGER PRIMARY KEY,
        project_id INTEGER NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
        title TEXT NOT NULL,
        sort_order INTEGER NOT NULL DEFAULT 0,
        body TEXT NOT NULL DEFAULT '',
        created_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ','now')),
        updated_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ','now'))
      );
      CREATE INDEX idx_scenes_project ON scenes(project_id, sort_order);

      CREATE TABLE cards (
        id INTEGER PRIMARY KEY,
        project_id INTEGER NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
        kind TEXT NOT NULL,
        title TEXT NOT NULL,
        content TEXT NOT NULL DEFAULT '',
        created_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ','now')),
        updated_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ','now'))
      );
      CREATE INDEX idx_cards_project ON cards(project_id, kind);

      CREATE TABLE card_relations (
        id INTEGER PRIMARY KEY,
        project_id INTEGER NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
        from_card_id INTEGER NOT NULL REFERENCES cards(id) ON DELETE CASCADE,
        to_card_id INTEGER NOT NULL REFERENCES cards(id) ON DELETE CASCADE,
        relation_type TEXT NOT NULL,
        UNIQUE(from_card_id, to_card_id, relation_type)
      );
      CREATE INDEX idx_card_relations_from ON card_relations(from_card_id);
      CREATE INDEX idx_card_relations_to ON card_relations(to_card_id);

      CREATE VIRTUAL TABLE scenes_fts USING fts5(
        title, body,
        content='scenes',
        content_rowid='id'
      );

      CREATE TRIGGER scenes_ai AFTER INSERT ON scenes BEGIN
        INSERT INTO scenes_fts(rowid, title, body) VALUES (new.id, new.title, new.body);
      END;
      CREATE TRIGGER scenes_ad AFTER DELETE ON scenes BEGIN
        INSERT INTO scenes_fts(scenes_fts, rowid, title, body) VALUES ('delete', old.id, old.title, old.body);
      END;
      CREATE TRIGGER scenes_au AFTER UPDATE ON scenes BEGIN
        INSERT INTO scenes_fts(scenes_fts, rowid, title, body) VALUES ('delete', old.id, old.title, old.body);
        INSERT INTO scenes_fts(rowid, title, body) VALUES (new.id, new.title, new.body);
      END;
    `);
    phases.schemaMs = Date.now() - schemaStart;

    function countRows(table) {
      return db.prepare(`SELECT count(*) AS c FROM ${table}`).get().c;
    }

    // Seed data
    db.exec("BEGIN");
    db.prepare("INSERT INTO projects(title) VALUES (?)").run("Spike 测试项目");
    db.exec("COMMIT");

    const insScene = db.prepare(
      "INSERT INTO scenes(project_id, title, sort_order, body) VALUES (?, ?, ?, ?)"
    );
    const scenesStart = Date.now();
    db.exec("BEGIN");
    for (let i = 1; i <= SCENE_COUNT; i += 1) {
      insScene.run(
        1,
        `场景 ${i}`,
        i,
        `雾谷钟声在雨夜响起，frag${pad4(i)} 穿过石桥，看见灯塔。`
      );
    }
    db.exec("COMMIT");
    phases.scenesMs = Date.now() - scenesStart;

    const insCard = db.prepare(
      "INSERT INTO cards(project_id, kind, title, content) VALUES (?, ?, ?, ?)"
    );
    const cardsStart = Date.now();
    db.exec("BEGIN");
    for (let i = 1; i <= CARD_COUNT; i += 1) {
      insCard.run(1, KINDS[i % KINDS.length], `卡片 ${i}`, `cardtok${i} 的设定内容`);
    }
    db.exec("COMMIT");
    phases.cardsMs = Date.now() - cardsStart;

    const insRelation = db.prepare(
      "INSERT INTO card_relations(project_id, from_card_id, to_card_id, relation_type) VALUES (?, ?, ?, ?)"
    );
    const relationsStart = Date.now();
    db.exec("BEGIN");
    for (let i = 0; i < RELATION_COUNT; i += 1) {
      // Two deterministic disjoint batches so the UNIQUE(from,to,type) never collides.
      const fromId = (i % CARD_COUNT) + 1;
      const toId = ((i + 5000) % CARD_COUNT) + 1;
      const type = i < CARD_COUNT ? "mentions" : "knows";
      insRelation.run(1, fromId, toId, type);
    }
    db.exec("COMMIT");
    phases.relationsMs = Date.now() - relationsStart;

    // Transaction rollback verification
    const rollbackStart = Date.now();
    db.exec("BEGIN");
    for (let i = SCENE_COUNT + 1; i <= SCENE_COUNT + 100; i += 1) {
      insScene.run(1, `回滚场景 ${i}`, i, `frag${pad4(i)} 不应存在`);
    }
    for (let i = CARD_COUNT + 1; i <= CARD_COUNT + 1000; i += 1) {
      insCard.run(1, KINDS[i % KINDS.length], `回滚卡片 ${i}`, `cardtok${i} 不应存在`);
    }
    db.exec("ROLLBACK");
    const rollbackScenes = countRows("scenes");
    const rollbackCards = countRows("cards");
    const rollbackVerified =
      rollbackScenes === SCENE_COUNT && rollbackCards === CARD_COUNT;
    const rollbackFtsClean =
      db.prepare("SELECT count(*) AS c FROM scenes_fts WHERE scenes_fts MATCH ?").get("frag2001").c === 0;
    phases.rollbackMs = Date.now() - rollbackStart;

    // Foreign key verification: invalid target must be rejected, valid link must work.
    const fkStart = Date.now();
    let fkRejected = false;
    db.exec("BEGIN");
    try {
      insRelation.run(1, 1, 999999, "knows");
    } catch (err) {
      fkRejected = err && err.code === "SQLITE_CONSTRAINT_FOREIGNKEY";
    }
    insRelation.run(1, 2, 3, "allies");
    const fkPositive = countRows("card_relations") === RELATION_COUNT + 1;
    db.exec("ROLLBACK");
    const fkVerified = fkRejected && fkPositive;
    phases.fkMs = Date.now() - fkStart;

    // FTS5 verification against external-content index kept in sync by triggers.
    const ftsStart = Date.now();
    const ftsMatch = db.prepare(
      "SELECT count(*) AS c FROM scenes_fts WHERE scenes_fts MATCH ?"
    );
    const hitFirst = ftsMatch.get("frag0001").c;
    const hitLast = ftsMatch.get(`frag${pad4(SCENE_COUNT)}`).c;
    const miss = ftsMatch.get("frag9999").c;
    const ftsVerified = hitFirst === 1 && hitLast === 1 && miss === 0;
    // Throws on mismatch; no exception means the FTS index is consistent with content.
    db.prepare("INSERT INTO scenes_fts(scenes_fts) VALUES('integrity-check')").run();
    const ftsIntegrityOk = true;
    phases.ftsMs = Date.now() - ftsStart;

    // Whole-database integrity + foreign key check
    const integrityStart = Date.now();
    const integrityOk = db.pragma("integrity_check", { simple: true }) === "ok";
    const fkCheckClean = db.pragma("foreign_key_check").length === 0;
    phases.integrityMs = Date.now() - integrityStart;

    const counts = {
      projects: countRows("projects"),
      scenes: countRows("scenes"),
      cards: countRows("cards"),
      relations: countRows("card_relations")
    };

    const allOk =
      integrityOk &&
      fkCheckClean &&
      journalMode === "wal" &&
      foreignKeysEnabled &&
      rollbackVerified &&
      rollbackFtsClean &&
      fkVerified &&
      ftsVerified &&
      ftsIntegrityOk &&
      counts.projects === 1 &&
      counts.scenes === SCENE_COUNT &&
      counts.cards === CARD_COUNT &&
      counts.relations === RELATION_COUNT;

    if (!allOk) {
      throw new Error(
        "Verification failed: " +
          JSON.stringify({
            integrityOk,
            fkCheckClean,
            journalMode,
            foreignKeysEnabled,
            rollbackVerified,
            rollbackFtsClean,
            fkVerified,
            ftsVerified,
            ftsIntegrityOk,
            counts,
            fts: { hitFirst, hitLast, miss }
          })
      );
    }

    phases.totalMs = Date.now() - startedAt;
    jsonLine = JSON.stringify({
      integrity: "ok",
      journalMode,
      foreignKeysEnabled,
      rollbackVerified: true,
      rollbackFtsClean: true,
      fkVerified: true,
      ftsVerified: true,
      ftsIntegrityCheck: "ok",
      foreignKeyCheckClean: true,
      counts,
      phases
    });
    ok = true;
  } catch (err) {
    process.stderr.write(
      `[creation-sqlite-spike] FAILED: ${err && err.stack ? err.stack : String(err)}\n`
    );
    process.exitCode = 1;
  } finally {
    cleanup();
    if (ok) {
      process.stdout.write(`${jsonLine}\n`);
      app.quit();
    } else {
      // app.quit() ignores process.exitCode on Windows; app.exit(1) guarantees non-zero.
      app.exit(1);
    }
  }
});
