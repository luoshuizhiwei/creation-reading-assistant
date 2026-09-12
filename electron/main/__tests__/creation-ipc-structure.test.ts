import { beforeEach, describe, expect, it, vi } from "vitest";

const electronMock = vi.hoisted(() => {
  const handlers = new Map<string, (...args: unknown[]) => unknown>();
  return {
    handlers,
    ipcMain: {
      handle: vi.fn((channel: string, handler: (...args: unknown[]) => unknown) => {
        handlers.set(channel, handler);
      })
    }
  };
});

vi.mock("electron", () => ({
  ipcMain: electronMock.ipcMain,
  dialog: {
    showOpenDialog: vi.fn(),
    showSaveDialog: vi.fn()
  },
  BrowserWindow: {
    fromWebContents: vi.fn(() => null)
  }
}));

import { registerCreationIpc } from "../creation-ipc";
import { CreationWorkspaceError } from "../creation-workspace";
import type { CreationCoordinator } from "../creation-coordinator";

describe("creation structure IPC input validation", () => {
  const previewStructure = vi.fn(async () => ({ ok: true }));
  const applyStructure = vi.fn(async () => ({ ok: true }));
  const revertStructure = vi.fn(async () => ({ ok: true }));
  const transact = vi.fn(async (command: unknown) => ({ command }));

  beforeEach(() => {
    electronMock.handlers.clear();
    electronMock.ipcMain.handle.mockClear();
    previewStructure.mockClear();
    applyStructure.mockClear();
    revertStructure.mockClear();
    transact.mockClear();

    const workspace = { previewStructure, applyStructure, revertStructure, transact };
    const coordinator = {
      withWorkspace: vi.fn(async (operation: (value: typeof workspace) => unknown) => operation(workspace))
    } as unknown as CreationCoordinator;

    registerCreationIpc(coordinator, {
      resolveDataRoot: () => "D:/data",
      resolveLibraryRoot: () => "D:/library"
    });
  });

  function invoke(channel: string, input: unknown): Promise<unknown> {
    const handler = electronMock.handlers.get(channel);
    if (!handler) throw new Error(`missing handler: ${channel}`);
    return Promise.resolve().then(() => handler({}, input));
  }

  it("accepts the three valid protected structure request shapes", async () => {
    const preview = {
      type: "structure.preview",
      projectId: "project-1",
      command: { type: "chapter.split", chapterId: "chapter-1", splitSceneId: "scene-2" }
    };
    const apply = {
      type: "structure.applyWithProtection",
      projectId: "project-1",
      planId: "plan-1",
      protectionReason: "拆章前自动保护"
    };
    const revert = {
      type: "structure.revert",
      projectId: "project-1",
      protectionSnapshotId: "snapshot-1",
      expectedAppliedRevisions: [{ type: "chapter", id: "chapter-1", revision: 2 }]
    };

    await invoke("creation:structurePreview", preview);
    await invoke("creation:structureApply", apply);
    await invoke("creation:structureRevert", revert);

    expect(previewStructure).toHaveBeenCalledWith(preview);
    expect(applyStructure).toHaveBeenCalledWith(apply);
    expect(revertStructure).toHaveBeenCalledWith(revert);
  });

  it.each([
    [
      "伪造预览 discriminator",
      "creation:structurePreview",
      { type: "structure.applyWithProtection", projectId: "project-1", command: { type: "chapter.split" } },
      previewStructure
    ],
    [
      "空 planId",
      "creation:structureApply",
      {
        type: "structure.applyWithProtection",
        projectId: "project-1",
        planId: "   ",
        protectionReason: "自动保护"
      },
      applyStructure
    ],
    [
      "负 revision",
      "creation:structureRevert",
      {
        type: "structure.revert",
        projectId: "project-1",
        protectionSnapshotId: "snapshot-1",
        expectedAppliedRevisions: [{ type: "scene", id: "scene-1", revision: -1 }]
      },
      revertStructure
    ],
    [
      "非受保护命令",
      "creation:structurePreview",
      {
        type: "structure.preview",
        projectId: "project-1",
        command: { type: "chapter.delete", chapterId: "chapter-1" }
      },
      previewStructure
    ]
  ])("rejects %s before entering the workspace", async (_label, channel, input, workspaceMethod) => {
    await expect(invoke(channel as string, input)).rejects.toMatchObject<Partial<CreationWorkspaceError>>({
      code: "invalid-input"
    });
    expect(workspaceMethod).not.toHaveBeenCalled();
  });

  it("validates and forwards card link/unlink commands through the public IPC seam", async () => {
    await invoke("creation:cardLink", { projectId: "project-1", cardId: "card-1" });
    await invoke("creation:cardUnlink", { projectId: "project-1", cardId: "card-1" });

    expect(transact).toHaveBeenNthCalledWith(1, {
      type: "card.link",
      projectId: "project-1",
      cardId: "card-1"
    });
    expect(transact).toHaveBeenNthCalledWith(2, {
      type: "card.unlink",
      projectId: "project-1",
      cardId: "card-1"
    });
  });

  it.each([
    [null],
    [{ projectId: "", cardId: "card-1" }],
    [{ projectId: "project-1", cardId: "   " }],
    [{ projectId: 1, cardId: "card-1" }]
  ])("rejects invalid card association input before entering the workspace: %j", async (input) => {
    await expect(invoke("creation:cardLink", input)).rejects.toMatchObject<Partial<CreationWorkspaceError>>({
      code: "invalid-input"
    });
    expect(transact).not.toHaveBeenCalled();
  });
});
