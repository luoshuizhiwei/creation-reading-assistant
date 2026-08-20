declare module "better-sqlite3" {
  interface DatabaseOptions {
    readonly?: boolean;
  }

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
    constructor(filename: string, options?: DatabaseOptions);
    close(): void;
    exec(sql: string): this;
    function(
      name: string,
      options: { deterministic?: boolean; varargs?: boolean; safeIntegers?: boolean },
      callback: (...params: string[]) => number | string | bigint | Buffer | null
    ): this;
    pragma(source: string, options?: { simple?: boolean }): unknown;
    prepare(sql: string): Statement;
  }
}
