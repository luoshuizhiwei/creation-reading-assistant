import { randomUUID } from "node:crypto";
import {
  CreationWorkspaceError,
  openCreationWorkspace,
  type CreationWatchScope,
  type CreationWorkspace,
  type CreationWorkspaceListener
} from "../creation-workspace";

export interface CreationCoordinator {
  getWorkspace(): Promise<CreationWorkspace>;
  /** 排队执行普通读/写操作；维护开始后新请求立即以 closed 拒绝，维护前已排队的操作先完成。 */
  withWorkspace<T>(op: (workspace: CreationWorkspace) => Promise<T>): Promise<T>;
  /** 维护期独占：等待已排队操作完成，flush（close/checkpoint）后再执行目录级操作。 */
  withWorkspaceClosed<T>(op: () => Promise<T>): Promise<T>;
  /** 订阅已提交事件；订阅跨维护 close/reopen 自动重绑。 */
  watch(scope: CreationWatchScope, listener: CreationWorkspaceListener): Promise<() => void>;
  close(): Promise<void>;
}

export interface CreateCreationCoordinatorOptions {
  resolveDirectory: () => string;
}

interface WatchRegistration {
  id: string;
  scope: CreationWatchScope;
  listener: CreationWorkspaceListener;
  unwatchCurrent?: () => void;
}

export function createCreationCoordinator(options: CreateCreationCoordinatorOptions): CreationCoordinator {
  let workspacePromise: Promise<CreationWorkspace> | undefined;
  let maintenanceDepth = 0;
  let operationTail: Promise<void> = Promise.resolve();
  const watchRegistrations = new Map<string, WatchRegistration>();

  const rebindWatchers = (workspace: CreationWorkspace): void => {
    for (const registration of watchRegistrations.values()) {
      // 先退订旧 workspace 的 watcher 再注册到新 workspace，避免重复订阅导致事件双发。
      registration.unwatchCurrent?.();
      registration.unwatchCurrent = workspace.watch(registration.scope, registration.listener);
    }
  };

  const openWorkspace = (): Promise<CreationWorkspace> => {
    if (!workspacePromise) {
      workspacePromise = openCreationWorkspace({ directory: options.resolveDirectory() })
        .then((workspace) => {
          rebindWatchers(workspace);
          return workspace;
        })
        .catch((error) => {
          workspacePromise = undefined;
          throw error;
        });
    }
    return workspacePromise;
  };

  const enqueue = <T>(op: () => Promise<T>): Promise<T> => {
    const run = operationTail.then(op);
    operationTail = run.then(
      () => undefined,
      () => undefined
    );
    return run;
  };

  const getWorkspace = (): Promise<CreationWorkspace> => {
    if (maintenanceDepth > 0) {
      return Promise.reject(new CreationWorkspaceError("closed", "创作工作区正在维护。"));
    }
    return openWorkspace();
  };

  const withWorkspace = <T>(op: (workspace: CreationWorkspace) => Promise<T>): Promise<T> => {
    if (maintenanceDepth > 0) {
      return Promise.reject(new CreationWorkspaceError("closed", "创作工作区正在维护。"));
    }
    return enqueue(async () => {
      const workspace = await openWorkspace();
      return op(workspace);
    });
  };

  const flush = async (): Promise<void> => {
    const pending = workspacePromise;
    workspacePromise = undefined;
    if (!pending) return;
    const workspace = await pending.catch(() => undefined);
    if (workspace) await workspace.close();
  };

  const withWorkspaceClosed = <T>(op: () => Promise<T>): Promise<T> => {
    maintenanceDepth += 1;
    return enqueue(async () => {
      try {
        await flush();
        return await op();
      } finally {
        maintenanceDepth -= 1;
      }
    });
  };

  const watch = async (scope: CreationWatchScope, listener: CreationWorkspaceListener): Promise<() => void> => {
    if (maintenanceDepth > 0) {
      throw new CreationWorkspaceError("closed", "创作工作区正在维护。");
    }
    const runtimeScope = scope as unknown as { projectId?: unknown } | null;
    if (
      runtimeScope === null ||
      typeof runtimeScope !== "object" ||
      typeof listener !== "function" ||
      (runtimeScope.projectId !== undefined &&
        (typeof runtimeScope.projectId !== "string" || !runtimeScope.projectId.trim()))
    ) {
      throw new CreationWorkspaceError("invalid-input", "创作工作区订阅请求无效。");
    }
    const registration: WatchRegistration = {
      id: `watch-${randomUUID()}`,
      scope: runtimeScope.projectId === undefined ? {} : { projectId: runtimeScope.projectId as string },
      listener
    };
    let cancelled = false;
    watchRegistrations.set(registration.id, registration);
    await enqueue(async () => {
      const workspace = await openWorkspace();
      if (cancelled) return;
      if (!registration.unwatchCurrent) {
        registration.unwatchCurrent = workspace.watch(registration.scope, registration.listener);
      }
    }).catch((error) => {
      watchRegistrations.delete(registration.id);
      throw error;
    });
    return () => {
      watchRegistrations.delete(registration.id);
      if (registration.unwatchCurrent) registration.unwatchCurrent();
      else cancelled = true;
    };
  };

  const close = (): Promise<void> => withWorkspaceClosed(async () => undefined);

  return { getWorkspace, withWorkspace, withWorkspaceClosed, watch, close };
}
