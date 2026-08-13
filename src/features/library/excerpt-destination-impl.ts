import { getDesktopApi } from "@/services/ipc-client";
import type { ExcerptResult, ExcerptSourceSnapshot, ReaderExcerptDestination } from "@/types/library";
import {
  buildInboxCreateCommand,
  buildCardCreateCommand,
  type ExcerptCommandExecutor,
  executeSaveToInbox,
  executeSaveToProjectCard
} from "./excerpt-commands";

/**
 * 生产环境摘录目的地：映射到现有创作工作台 IPC。
 *
 * 实现委托给共享深模块 excerpt-commands.ts，
 * 与 Electron contract（reader-excerpt-contract.ts）共用同一套
 * 命令构造、entityId 读取、失败归一化逻辑——
 * 不再在两处复制 sourceToSnapshot / saveToInbox / saveToProjectCard。
 *
 * - saveToInbox → creation.inboxCreate（通过共享 executeSaveToInbox）
 * - saveToProjectCard → creation.runStructure(card.create)（通过共享 executeSaveToProjectCard）
 * - listProjects → creation.listProjects
 *
 * 失败时返回 success=false + error，绝不假成功。
 */

/**
 * 把 getDesktopApi().creation 包装成 ExcerptCommandExecutor。
 * 这样共享深模块不直接依赖 preload IPC 形状，contract 也可以注入 workspace 实现。
 */
function createDesktopApiExecutor(): ExcerptCommandExecutor {
  return {
    async inboxCreate(command) {
      const api = getDesktopApi().creation;
      // desktopApi.inboxCreate 接收 Omit<InboxCreateCommand, "type">，我们透传全部字段
      return api.inboxCreate(command);
    },
    async cardCreate(command) {
      const api = getDesktopApi().creation;
      // runStructure 接收结构化命令，返回 StructureCommandResult（含 entityId）
      return api.runStructure(command) as Promise<{ entityId?: string; commandType?: string; cardId?: string }>;
    }
  };
}

export function createReaderExcerptDestination(): ReaderExcerptDestination {
  return {
    async listProjects() {
      const projects = await getDesktopApi().creation.listProjects();
      return projects.map((project) => ({ id: project.id, title: project.title }));
    },

    async saveToInbox(source: ExcerptSourceSnapshot): Promise<ExcerptResult> {
      const executor = createDesktopApiExecutor();
      return executeSaveToInbox(executor, source);
    },

    async saveToProjectCard(projectId: string, source: ExcerptSourceSnapshot): Promise<ExcerptResult> {
      const executor = createDesktopApiExecutor();
      return executeSaveToProjectCard(executor, projectId, source);
    }
  };
}
