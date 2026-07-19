import { Capacitor } from "@capacitor/core";
import { CapacitorSQLite, SQLiteConnection, type SQLiteDBConnection } from "@capacitor-community/sqlite";
import { MOBILE_DATABASE_NAME, MOBILE_SCHEMA_STATEMENTS, MOBILE_SCHEMA_VERSION } from "./mobile-schema";

export interface MobileDatabase {
  db: SQLiteDBConnection;
  run(statement: string, values?: unknown[]): Promise<void>;
  query<T>(statement: string, values?: unknown[]): Promise<T[]>;
  close(): Promise<void>;
}

let sqlite: SQLiteConnection | undefined;
let db: SQLiteDBConnection | undefined;
let openPromise: Promise<MobileDatabase | undefined> | undefined;

function canUseNativeSQLite(): boolean {
  return Capacitor.getPlatform() !== "web";
}

export async function openMobileDatabase(): Promise<MobileDatabase | undefined> {
  if (!canUseNativeSQLite()) return undefined;
  if (openPromise) return openPromise;
  openPromise = (async () => {
    sqlite ??= new SQLiteConnection(CapacitorSQLite);
    const existing = await sqlite.isConnection(MOBILE_DATABASE_NAME, false).catch(() => ({ result: false }));
    db = existing.result
      ? await sqlite.retrieveConnection(MOBILE_DATABASE_NAME, false)
      : await sqlite.createConnection(MOBILE_DATABASE_NAME, false, "no-encryption", MOBILE_SCHEMA_VERSION, false);

    const isOpen = await db.isDBOpen().catch(() => ({ result: false }));
    if (!isOpen.result) await db.open();
    await db.execute(MOBILE_SCHEMA_STATEMENTS.join("\n"), true);

    return {
      db,
      async run(statement: string, values: unknown[] = []) {
        await db?.run(statement, values as never[], true);
      },
      async query<T>(statement: string, values: unknown[] = []) {
        const result = await db?.query(statement, values as never[]);
        return ((result?.values ?? []) as T[]).filter(Boolean);
      },
      async close() {
        await sqlite?.closeConnection(MOBILE_DATABASE_NAME, false).catch(() => undefined);
        db = undefined;
        openPromise = undefined;
      }
    };
  })().catch((error) => {
    db = undefined;
    openPromise = undefined;
    throw error;
  });
  return openPromise;
}

export async function resetMobileDatabaseConnection(): Promise<void> {
  if (!sqlite) return;
  await sqlite.closeConnection(MOBILE_DATABASE_NAME, false).catch(() => undefined);
  db = undefined;
  openPromise = undefined;
}
