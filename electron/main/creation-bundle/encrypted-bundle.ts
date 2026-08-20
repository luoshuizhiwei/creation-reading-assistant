/**
 * 项目包加密集成层（薄封装）。
 *
 * 把现有明文项目包目录（如 `exportProjectBundleDirectory` 产出）包装为受口令保护的
 * 容器，以及把容器解密到临时目录后再交还调用方执行现有 `importProjectBundleDirectory`
 * 验证/落盘流程。本文件不修改 operation coordinator 或公共 seam；进度/取消通过
 * `OperationController`（结构上满足 `EncryptionProgress`）透传给深模块。
 *
 * 明文 v1/v2 项目包格式保持不变，因此继续兼容（要求 1）。
 */
import type { OperationController } from "../operation";
import {
  encryptDirectory,
  withDecryptedStaging,
  EncryptionError
} from "../portable-encryption";
import type { Logger } from "../portable-encryption/types";

export interface EncryptBundleContainerOptions {
  /** 明文项目包目录（bundle-directory）。 */
  bundleDirectory: string;
  /** 输出的加密容器文件路径，建议扩展名 `.crbundle`。 */
  targetFile: string;
  passphrase: string;
  operation?: OperationController;
  logger?: Logger;
}

export interface DecryptBundleContainerOptions {
  containerFile: string;
  passphrase: string;
  operation?: OperationController;
  logger?: Logger;
}

/** 把明文项目包目录加密为容器文件。 */
export async function encryptBundleToContainer(opts: EncryptBundleContainerOptions) {
  return encryptDirectory({
    sourceDir: opts.bundleDirectory,
    targetFile: opts.targetFile,
    passphrase: opts.passphrase,
    payloadKind: "bundle",
    operation: opts.operation,
    logger: opts.logger
  });
}

/**
 * 校验容器完整性并解密到受控临时目录，回调内执行现有项目包导入流程。
 * 只有密文完整性通过后才进入回调（要求 12）。临时目录保证清理（要求 7）。
 */
export async function withDecryptedBundleStaging<T>(
  opts: DecryptBundleContainerOptions,
  fn: (stagingDir: string) => Promise<T>
): Promise<T> {
  return withDecryptedStaging(
    {
      containerFile: opts.containerFile,
      passphrase: opts.passphrase,
      operation: opts.operation,
      logger: opts.logger
    },
    (stagingDir) => fn(stagingDir)
  );
}

export { EncryptionError };
