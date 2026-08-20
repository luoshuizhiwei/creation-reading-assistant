/**
 * 流式归档（深模块内部）: 把目录序列化为二进制帧，再交给 AEAD 加密。
 *
 * 帧格式（位于密文内部，因此本身也受 GCM 完整性保护）:
 *   u32 BE  pathLen      (相对路径字节长度, >0)
 *   bytes   path         (UTF-8 相对路径)
 *   u64 BE  size         (文件字节数)
 *   bytes   content      (size 字节文件内容)
 * 终止符:
 *   u32 BE  0
 *
 * 解档器 `ArchiveExtractor` 以流式方式写入目标文件，单个大文件分多个 chunk 落盘，
 * 不会把整个文件载入内存。
 */
import { createReadStream, createWriteStream, promises as fsp, mkdirSync } from "node:fs";
import path from "node:path";
import { Buffer } from "node:buffer";
import { Readable, Writable } from "node:stream";

export interface ArchiveEntry {
  relativePath: string;
  absolutePath: string;
  size: number;
}

const KIB = 64 * 1024;

export function readUInt64BE(buf: Buffer, offset: number): bigint {
  const high = buf.readUInt32BE(offset);
  const low = buf.readUInt32BE(offset + 4);
  return (BigInt(high) << 32n) + BigInt(low);
}

export function writeUInt64BE(buf: Buffer, offset: number, value: bigint): void {
  const v = value & 0xffffffffffffffffn;
  buf.writeUInt32BE(Number(v >> 32n), offset);
  buf.writeUInt32BE(Number(v & 0xffffffffn), offset + 4);
}

/** 拒绝绝对路径与 ".." 穿越，防御性校验（容器本身受 GCM 保护，此为纵深防御）。 */
export function safeRelative(rel: string): string {
  if (path.isAbsolute(rel)) throw new Error("unsafe path: absolute");
  const normalized = path.normalize(rel);
  if (normalized === ".." || normalized.startsWith(`..${path.sep}`) || normalized.startsWith("..")) {
    throw new Error("unsafe path: traversal");
  }
  return normalized;
}

/** 递归列举常规文件（跳过符号链接），返回按相对路径排序的条目列表。 */
export async function walkFiles(root: string): Promise<ArchiveEntry[]> {
  const out: ArchiveEntry[] = [];
  async function recurse(dir: string): Promise<void> {
    const dirents = await fsp.readdir(dir, { withFileTypes: true });
    for (const e of dirents) {
      const abs = path.join(dir, e.name);
      if (e.isSymbolicLink()) continue;
      if (e.isDirectory()) {
        await recurse(abs);
      } else if (e.isFile()) {
        const st = await fsp.stat(abs);
        const rel = path.relative(root, abs).split(path.sep).join("/");
        out.push({ relativePath: rel, absolutePath: abs, size: st.size });
      }
    }
  }
  await recurse(root);
  out.sort((a, b) => a.relativePath.localeCompare(b.relativePath));
  return out;
}

/**
 * 把条目流式生成为归档字节。每处理一个文件会调用 `checkCancel()` 以尊重取消；
 * 每写出 `bytes` 明文调用 `onChunk(bytes)` 以累计进度。
 */
export function createArchiveReadable(
  entries: ArchiveEntry[],
  onChunk?: (bytes: number) => void,
  checkCancel?: () => void
): Readable {
  return Readable.from(
    (async function* gen() {
      for (const entry of entries) {
        checkCancel?.();
        const pathBuf = Buffer.from(entry.relativePath, "utf8");
        const header = Buffer.alloc(4 + pathBuf.length + 8);
        header.writeUInt32BE(pathBuf.length, 0);
        pathBuf.copy(header, 4);
        writeUInt64BE(header, 4 + pathBuf.length, BigInt(entry.size));
        yield header;
        const rs = createReadStream(entry.absolutePath, { highWaterMark: KIB });
        for await (const chunk of rs) {
          const b = chunk as Buffer;
          yield b;
          onChunk?.(b.length);
        }
      }
      yield Buffer.from([0, 0, 0, 0]);
    })()
  );
}

/**
 * 流式解档器: 从解密后的归档字节中提取文件到 stagingDir。
 * 单个大文件的内容跨多个 chunk 增量落盘，内存占用有界。
 */
export class ArchiveExtractor extends Writable {
  private buf: Buffer = Buffer.alloc(0);
  private active: { ws: import("node:fs").WriteStream; remaining: bigint } | null = null;
  private done = false;
  private readonly openStreams = new Set<import("node:fs").WriteStream>();

  constructor(
    private readonly stagingDir: string,
    private readonly onProgress?: (bytes: number) => void
  ) {
    super();
  }

  /** 等待所有已写出文件流完全关闭，避免在 Windows 上因句柄未释放导致 rename/rm 报 EPERM。 */
  async allClosed(): Promise<void> {
    for (const ws of [...this.openStreams]) {
      if (!ws.closed) {
        // 解密失败等情况下活动流可能从未收到 end()，需强制关闭以触发 close 事件。
        if (!ws.writableEnded) {
          ws.destroy();
        }
        await new Promise<void>((resolve) => ws.on("close", () => resolve()));
      }
    }
  }

  _write(chunk: Buffer, _enc: string, cb: (error?: Error | null) => void): void {
    try {
      this.buf = this.buf.length === 0 ? chunk : Buffer.concat([this.buf, chunk]);
      this.drain();
    } catch (e) {
      this.destroy(e as Error);
      return;
    }
    cb();
  }

  _final(cb: (error?: Error | null) => void): void {
    try {
      this.drain();
      if (!this.done) throw new Error("archive truncated: missing terminator");
      if (this.active) throw new Error("archive truncated: incomplete file");
      if (this.buf.length > 0) throw new Error("archive truncated: trailing bytes");
    } catch (e) {
      cb(e as Error);
      return;
    }
    cb();
  }

  private drain(): void {
    // 循环处理已缓冲的帧头与文件内容。
    // eslint-disable-next-line no-constant-condition
    while (true) {
      if (this.active) {
        const toWrite = Math.min(Number(this.active.remaining), this.buf.length);
        if (toWrite > 0) {
          this.active.ws.write(this.buf.subarray(0, toWrite));
          this.active.remaining -= BigInt(toWrite);
          this.buf = this.buf.subarray(toWrite);
          this.onProgress?.(toWrite);
        }
        if (this.active.remaining === 0n) {
          this.active.ws.end();
          this.active = null;
          continue;
        }
        return;
      }
      if (this.buf.length < 4) return;
      const pathLen = this.buf.readUInt32BE(0);
      if (pathLen === 0) {
        this.buf = this.buf.subarray(4);
        this.done = true;
        return;
      }
      if (this.buf.length < 4 + pathLen + 8) return;
      const rel = this.buf.toString("utf8", 4, 4 + pathLen);
      const size = readUInt64BE(this.buf, 4 + pathLen);
      this.buf = this.buf.subarray(4 + pathLen + 8);
      const safe = safeRelative(rel);
      const target = path.join(this.stagingDir, safe);
      mkdirSync(path.dirname(target), { recursive: true });
      const ws = createWriteStream(target);
      ws.on("error", (e) => this.destroy(e as Error));
      ws.on("close", () => this.openStreams.delete(ws));
      this.openStreams.add(ws);
      this.active = { ws, remaining: size };
    }
  }
}
