/**
 * 可移植加密深模块。
 *
 * 设计要点:
 * - 对目录做“可选口令加密”的容器封装；明文内层格式（备份 v1/v2、项目包 v1/v2）保持不变，
 *   因此明文向后兼容（要求 1）。
 * - 容器头部（明文）包含 KDF/AEAD 参数、随机 salt 与 nonce（要求 2）。
 * - 使用 Node 官方 crypto: scrypt 派生密钥、AES-256-GCM 认证加密、每次随机 salt/nonce（要求 3）。
 * - 不保存口令、派生密钥或可恢复明文；密钥用完即清零（要求 4）。
 * - 错误口令 / 篡改密文 / 截断文件在写入最终目标前失败（GCM 认证失败，要求 5/12）。
 * - 解密先落到受控临时目录，失败时立即清理，不会留下半成品目录或半恢复数据（要求 6/7）。
 * - 接入调用方 operation 的进度与取消，但不依赖 operation coordinator 实现（要求 8）。
 * - 大文件流式处理，不整体载入内存（要求 11）。
 * - 日志与错误不含口令、密钥或正文（要求 10）。
 */
import { randomBytes, scryptSync, createCipheriv, createDecipheriv } from "node:crypto";
import { createReadStream, createWriteStream, promises as fsp } from "node:fs";
import os from "node:os";
import path from "node:path";
import { Buffer } from "node:buffer";
import { ArchiveExtractor, walkFiles, createArchiveReadable } from "./archive";
import type {
  DecryptOptions,
  DecryptResult,
  EncryptOptions,
  EncryptionErrorCode,
  EncryptionProgress,
  Logger,
  PayloadKind
} from "./types";

export const CONTAINER_MAGIC = Buffer.from("CRPK", "ascii");
export const CONTAINER_FORMAT_MAJOR = 1;
export const CONTAINER_FORMAT_MINOR = 0;

const SALT_LEN = 16;
const KEYLEN = 32;
const GCM_IV_LEN = 12;
const GCM_TAG_LEN = 16;
const SCRYPT = { N: 32768, r: 8, p: 1 };

interface ContainerHeader {
  v: number;
  kdf: { algo: string; N: number; r: number; p: number; keylen: number; salt: string };
  aead: { algo: string; ivLen: number; tagLen: number; iv: string };
  payload: { kind?: PayloadKind; innerVersion?: number; entries?: number; bytes?: number };
}

export class EncryptionError extends Error {
  code: EncryptionErrorCode;
  constructor(code: EncryptionErrorCode, message: string) {
    super(message);
    this.code = code;
    this.name = "EncryptionError";
  }
}

function isCancel(e: unknown): boolean {
  if (e instanceof EncryptionError && e.code === "cancelled") return true;
  const anyErr = e as { code?: string; name?: string } | null;
  if (anyErr && (anyErr.code === "cancelled" || anyErr.name === "OperationCancelledError")) return true;
  return false;
}

async function safeRemoveDir(dir: string): Promise<void> {
  for (let attempt = 0; attempt < 6; attempt++) {
    try {
      await fsp.rm(dir, { recursive: true, force: true });
      return;
    } catch (e) {
      const code = (e as NodeJS.ErrnoException)?.code;
      if (code === "ENOENT") return;
      if (attempt === 5) return; // 放弃，best effort
      await new Promise((r) => setTimeout(r, 80 * (attempt + 1)));
    }
  }
}

async function safeRemoveFile(file: string): Promise<void> {
  for (let attempt = 0; attempt < 6; attempt++) {
    try {
      await fsp.rm(file, { force: true });
      return;
    } catch (e) {
      const code = (e as NodeJS.ErrnoException)?.code;
      if (code === "ENOENT") return;
      if (attempt === 5) return;
      await new Promise((r) => setTimeout(r, 80 * (attempt + 1)));
    }
  }
}

/**
 * 把明文目录加密为受口令保护的容器文件。
 * 失败时清理半写的容器文件；派生密钥在 finally 中清零。
 */
export async function encryptDirectory(opts: EncryptOptions): Promise<{
  targetFile: string;
  bytesTotal: number;
  fileCount: number;
}> {
  if (!opts.passphrase || opts.passphrase.length === 0) {
    throw new EncryptionError("invalid-input", "口令不能为空");
  }
  const logger: Logger | undefined = opts.logger;
  const op: EncryptionProgress | undefined = opts.operation;
  const entries = await walkFiles(opts.sourceDir);
  const bytesTotal = entries.reduce((s, e) => s + e.size, 0);
  await fsp.mkdir(path.dirname(opts.targetFile), { recursive: true });

  const salt = randomBytes(SALT_LEN);
  const iv = randomBytes(GCM_IV_LEN);
  const key = scryptSync(opts.passphrase, salt, KEYLEN, {
    N: SCRYPT.N,
    r: SCRYPT.r,
    p: SCRYPT.p,
    maxmem: 64 * 1024 * 1024
  });
  try {
    const header: ContainerHeader = {
      v: 1,
      kdf: { algo: "scrypt", N: SCRYPT.N, r: SCRYPT.r, p: SCRYPT.p, keylen: KEYLEN, salt: salt.toString("base64") },
      aead: { algo: "aes-256-gcm", ivLen: GCM_IV_LEN, tagLen: GCM_TAG_LEN, iv: iv.toString("base64") },
      payload: { kind: opts.payloadKind ?? "generic", innerVersion: opts.innerVersion, entries: entries.length, bytes: bytesTotal }
    };
    const headerBuf = Buffer.from(JSON.stringify(header), "utf8");
    const out = createWriteStream(opts.targetFile);
    const prefix = Buffer.alloc(10);
    CONTAINER_MAGIC.copy(prefix, 0);
    prefix.writeUInt8(CONTAINER_FORMAT_MAJOR, 4);
    prefix.writeUInt8(CONTAINER_FORMAT_MINOR, 5);
    prefix.writeUInt32BE(headerBuf.length, 6);
    out.write(prefix);
    out.write(headerBuf);

    const cipher = createCipheriv("aes-256-gcm", key, iv);
    let completed = 0;
    const finished = new Promise<void>((resolve, reject) => {
      cipher.on("data", (c: Buffer) => {
        if (!out.write(c)) cipher.pause();
      });
      out.on("drain", () => cipher.resume());
      cipher.on("end", () => {
        try {
          out.write(cipher.getAuthTag());
          out.end(() => resolve());
        } catch (e) {
          reject(e as Error);
        }
      });
      cipher.on("error", reject);
      out.on("error", reject);
    });

    op?.setProgress({ bytesCompleted: 0, bytesTotal, indeterminate: false });
    const archive = createArchiveReadable(
      entries,
      (bytes) => {
        completed += bytes;
        op?.setProgress({ bytesCompleted: completed, bytesTotal, indeterminate: false });
      },
      () => op?.throwIfCancelled()
    );
    archive.on("error", (e: Error) => cipher.destroy(e));
    archive.pipe(cipher);
    await finished;

    logger?.("info", "container-encrypted", {
      fileCount: entries.length,
      bytes: bytesTotal,
      kind: opts.payloadKind ?? "generic"
    });
    return { targetFile: opts.targetFile, bytesTotal, fileCount: entries.length };
  } catch (e) {
    await safeRemoveFile(opts.targetFile);
    if (e instanceof EncryptionError) throw e;
    if (isCancel(e)) throw new EncryptionError("cancelled", "已取消加密");
    throw new EncryptionError("invalid-input", `加密失败：${(e as Error)?.message ?? String(e)}`);
  } finally {
    key.fill(0);
  }
}

/**
 * 校验容器完整性（GCM 认证）并把内容解密到受控临时目录。
 * 失败（错误口令 / 篡改 / 截断 / 取消）时清理临时目录，绝不留下半成品数据。
 */
export async function decryptContainerToStaging(opts: DecryptOptions): Promise<DecryptResult> {
  const logger: Logger | undefined = opts.logger;
  const op: EncryptionProgress | undefined = opts.operation;
  const fileSize = (await fsp.stat(opts.containerFile)).size;
  const fd = await fsp.open(opts.containerFile, "r");
  try {
    const prefix = Buffer.alloc(10);
    await fd.read(prefix, 0, 10, 0);
    if (!prefix.subarray(0, 4).equals(CONTAINER_MAGIC)) {
      throw new EncryptionError("invalid-input", "不是受支持的加密容器（魔数不匹配）");
    }
    const major = prefix.readUInt8(4);
    const minor = prefix.readUInt8(5);
    if (major !== CONTAINER_FORMAT_MAJOR) {
      throw new EncryptionError("unsupported-format", `不支持的容器版本 ${major}.${minor}`);
    }
    const headerLen = prefix.readUInt32BE(6);
    if (headerLen <= 0 || headerLen > 1_000_000) {
      throw new EncryptionError("invalid-input", "容器头部长度异常");
    }
    const headerBuf = Buffer.alloc(headerLen);
    await fd.read(headerBuf, 0, headerLen, 10);
    let header: ContainerHeader;
    try {
      header = JSON.parse(headerBuf.toString("utf8")) as ContainerHeader;
    } catch {
      throw new EncryptionError("invalid-input", "容器头部损坏");
    }
    const headerEnd = 10 + headerLen;
    if (fileSize < headerEnd + GCM_TAG_LEN) {
      throw new EncryptionError("truncated", "容器文件被截断（缺少密文或认证标签）");
    }
    if (header.kdf?.algo !== "scrypt") throw new EncryptionError("unsupported-format", "不支持的 KDF");
    if (header.aead?.algo !== "aes-256-gcm") throw new EncryptionError("unsupported-format", "不支持的 AEAD");

    const salt = Buffer.from(header.kdf.salt, "base64");
    const iv = Buffer.from(header.aead.iv, "base64");
    const key = scryptSync(opts.passphrase, salt, header.kdf.keylen, {
      N: header.kdf.N,
      r: header.kdf.r,
      p: header.kdf.p,
      maxmem: 64 * 1024 * 1024
    });
    try {
      const stagingDir = await fsp.mkdtemp(path.join(os.tmpdir(), "cr-decrypt-"));
      try {
        const tagBuf = Buffer.alloc(GCM_TAG_LEN);
        await fd.read(tagBuf, 0, GCM_TAG_LEN, fileSize - GCM_TAG_LEN);
        const decipher = createDecipheriv("aes-256-gcm", key, iv);
        decipher.setAuthTag(tagBuf);
        const bytesTotal = fileSize - headerEnd - GCM_TAG_LEN;
        const ciphertext = createReadStream(opts.containerFile, {
          start: headerEnd,
          end: fileSize - GCM_TAG_LEN - 1
        });
        let completed = 0;
        op?.setProgress({ bytesCompleted: 0, bytesTotal, indeterminate: false });
        const extractor = new ArchiveExtractor(stagingDir, (bytes) => {
          completed += bytes;
          op?.setProgress({ bytesCompleted: completed, bytesTotal, indeterminate: false });
          try {
            op?.throwIfCancelled();
          } catch (c) {
            extractor.destroy(c as Error);
          }
        });
        try {
          await new Promise<void>((resolve, reject) => {
            let settled = false;
            const onErr = (err: Error) => {
              if (!settled) {
                settled = true;
                reject(err);
              }
            };
            ciphertext.on("error", onErr);
            decipher.on("error", onErr);
            extractor.on("error", onErr);
            extractor.on("finish", () => {
              if (!settled) {
                settled = true;
                resolve();
              }
            });
            ciphertext.pipe(decipher).pipe(extractor);
          });
        } finally {
          // 无论成功失败，先确保所有被写出文件流已关闭，避免下层 rm 时句柄未释放（Windows EPERM）。
          await extractor.allClosed();
        }

        logger?.("info", "container-decrypted", {
          kind: header.payload?.kind,
          innerVersion: header.payload?.innerVersion,
          stagingDir
        });
        return {
          stagingDir,
          payloadKind: (header.payload?.kind ?? "generic") as PayloadKind,
          innerVersion: header.payload?.innerVersion,
          fileCount: header.payload?.entries ?? 0,
          bytesTotal
        };
      } catch (e) {
        await safeRemoveDir(stagingDir);
        throw e;
      }
    } finally {
      key.fill(0);
    }
  } catch (e) {
    if (e instanceof EncryptionError) throw e;
    if (isCancel(e)) throw new EncryptionError("cancelled", "已取消解密");
    throw new EncryptionError("auth-failed", "密文校验失败：口令错误、密文被篡改或文件不完整");
  } finally {
    await fd.close();
  }
}

/**
 * 解密到临时目录并执行回调，无论成功失败都保证临时目录被清理（要求 7）。
 * 回调内应运行现有项目包/备份验证流程——只有密文完整性通过后才会到达此处（要求 12）。
 */
export async function withDecryptedStaging<T>(
  opts: DecryptOptions,
  fn: (stagingDir: string, meta: Omit<DecryptResult, "stagingDir">) => Promise<T>
): Promise<T> {
  const { stagingDir, ...meta } = await decryptContainerToStaging(opts);
  try {
    return await fn(stagingDir, meta);
  } finally {
    await safeRemoveDir(stagingDir);
  }
}
