const { app } = require("electron");
const path = require("node:path");

app.whenReady().then(() => {
  try {
    const moduleRoot = process.argv[2];
    if (!moduleRoot) throw new Error("Expected a better-sqlite3 package path.");
    const Database = require(path.resolve(moduleRoot));
    const database = new Database(":memory:");
    const sqliteVersion = database.prepare("SELECT sqlite_version() AS version").get().version;
    database.close();
    process.stdout.write(`${JSON.stringify({ loaded: true, sqliteVersion, runtime: process.versions.electron })}\n`);
    app.quit();
  } catch (error) {
    process.stderr.write(`${error instanceof Error ? error.stack : String(error)}\n`);
    app.exit(1);
  }
});

