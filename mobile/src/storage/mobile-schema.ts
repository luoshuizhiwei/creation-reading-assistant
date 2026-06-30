export const MOBILE_DATABASE_NAME = "creation_reading_assistant_mobile";
export const MOBILE_SCHEMA_VERSION = 1;

export const MOBILE_SCHEMA_STATEMENTS = [
  `CREATE TABLE IF NOT EXISTS books (
    id TEXT PRIMARY KEY NOT NULL,
    title TEXT NOT NULL,
    payload TEXT NOT NULL,
    content_hash TEXT,
    updated_at TEXT NOT NULL,
    deleted_at TEXT
  );`,
  `CREATE TABLE IF NOT EXISTS book_files (
    book_id TEXT PRIMARY KEY NOT NULL,
    file_name TEXT NOT NULL,
    format TEXT NOT NULL,
    payload TEXT NOT NULL,
    content_hash TEXT,
    size INTEGER NOT NULL,
    local_uri TEXT,
    updated_at TEXT NOT NULL,
    deleted_at TEXT
  );`,
  `CREATE TABLE IF NOT EXISTS reading_progress (
    book_id TEXT PRIMARY KEY NOT NULL,
    payload TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    deleted_at TEXT
  );`,
  `CREATE TABLE IF NOT EXISTS reading_sessions (
    id TEXT PRIMARY KEY NOT NULL,
    book_id TEXT NOT NULL,
    payload TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    deleted_at TEXT
  );`,
  `CREATE TABLE IF NOT EXISTS inspirations (
    id TEXT PRIMARY KEY NOT NULL,
    title TEXT NOT NULL,
    payload TEXT NOT NULL,
    source_book_id TEXT,
    updated_at TEXT NOT NULL,
    deleted_at TEXT
  );`,
  `CREATE TABLE IF NOT EXISTS inspiration_variants (
    id TEXT PRIMARY KEY NOT NULL,
    inspiration_id TEXT NOT NULL,
    payload TEXT NOT NULL,
    created_at TEXT NOT NULL
  );`,
  `CREATE TABLE IF NOT EXISTS notes (
    id TEXT PRIMARY KEY NOT NULL,
    book_id TEXT,
    inspiration_id TEXT,
    payload TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    deleted_at TEXT
  );`,
  `CREATE TABLE IF NOT EXISTS tags (
    id TEXT PRIMARY KEY NOT NULL,
    name TEXT NOT NULL,
    payload TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    deleted_at TEXT
  );`,
  `CREATE TABLE IF NOT EXISTS categories (
    id TEXT PRIMARY KEY NOT NULL,
    name TEXT NOT NULL,
    payload TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    deleted_at TEXT
  );`,
  `CREATE TABLE IF NOT EXISTS shelves (
    id TEXT PRIMARY KEY NOT NULL,
    name TEXT NOT NULL,
    payload TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    deleted_at TEXT
  );`,
  `CREATE TABLE IF NOT EXISTS sync_accounts (
    id TEXT PRIMARY KEY NOT NULL,
    provider TEXT NOT NULL,
    payload TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    deleted_at TEXT
  );`,
  `CREATE TABLE IF NOT EXISTS sync_state (
    key TEXT PRIMARY KEY NOT NULL,
    payload TEXT NOT NULL,
    updated_at TEXT NOT NULL
  );`
];

export function getMobileSchemaSql(): string {
  return MOBILE_SCHEMA_STATEMENTS.join("\n");
}
