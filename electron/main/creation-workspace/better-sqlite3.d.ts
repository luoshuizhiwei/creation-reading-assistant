declare module "better-sqlite3" {
  interface RunResult {
    changes: number;
    lastInsertRowid: number | bigint;
  }

  interface Statement {
    all(...params: unknown[]): unknown[];
    get(...params: unknown[]): unknown;
    run(...params: unknown[]): RunResult;
  }

  export default class Database {
    constructor(filename: string);
    close(): void;
    exec(sql: string): this;
    pragma(source: string, options?: { simple?: boolean }): unknown;
    prepare(sql: string): Statement;
  }
}
