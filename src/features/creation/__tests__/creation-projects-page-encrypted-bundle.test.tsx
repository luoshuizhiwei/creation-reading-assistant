// @vitest-environment jsdom
import React from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { CreationProjectsPage } from "@/features/creation/CreationProjectsPage";
import { useCreationStore } from "@/stores/creation-store";
import { useUIStore } from "@/stores/ui-store";

const service = vi.hoisted(() => ({
  listProjects: vi.fn(),
  readProjectHome: vi.fn(),
  inboxCount: vi.fn(),
  migrationStatus: vi.fn(),
  readProjectNavigation: vi.fn(),
  readProjectOutline: vi.fn(),
  readSceneBody: vi.fn(),
  updateSceneBody: vi.fn(),
  watchProject: vi.fn(),
  statsView: vi.fn(),
  sessionList: vi.fn(),
  createProject: vi.fn()
}));

vi.mock("@/services/creation-service", () => service);

const operation = vi.hoisted(() => ({
  startOperation: vi.fn(),
  getOperationState: vi.fn(),
  cancelOperation: vi.fn(),
  subscribeOperation: vi.fn()
}));

vi.mock("@/services/operation-service", () => ({
  startOperation: (request: Parameters<typeof operation.startOperation>[0]) => operation.startOperation(request),
  getOperationState: (id: string) => operation.getOperationState(id),
  cancelOperation: (id: string) => operation.cancelOperation(id),
  subscribeOperation: (id: string, listener: (state: unknown) => void) => operation.subscribeOperation(id, listener)
}));

vi.mock("@/hooks/useCreationActions", () => {
  const hookApi = {
    loadProjects: () => service.listProjects().then(() => undefined),
    loadProjectHome: () => service.readProjectHome(),
    loadInboxCount: () => service.inboxCount(),
    loadMigrationStatus: () => service.migrationStatus(),
    loadNavigation: (projectId: string) => service.readProjectNavigation(projectId),
    loadOutline: (projectId: string) => service.readProjectOutline(projectId),
    loadScene: (sceneId: string) => service.readSceneBody(sceneId),
    loadCards: () => Promise.resolve([]),
    loadStats: () => service.statsView(),
    loadSessions: () => service.sessionList(),
    saveSceneBody: () => service.updateSceneBody(),
    createProject: () => service.createProject(),
    subscribeProject: () => () => undefined
  };
  return { useCreationActions: () => hookApi };
});

vi.mock("@/features/creation/editor/WritingDesk", () => ({ WritingDesk: () => <div /> }));
vi.mock("@/features/creation/cards/CardsPage", () => ({ CardsPage: () => <div /> }));
vi.mock("@/features/creation/outline/OutlinePage", () => ({ OutlinePage: () => <div /> }));
vi.mock("@/features/creation/overview/OverviewPage", () => ({ OverviewPage: () => <div /> }));

const PROJECT = {
  id: "p1",
  title: "测试项目",
  setup: { template: "blank", weeklyUpdateDays: [], chapterWorkflow: ["规划"] },
  updatedAt: "",
  revision: 1,
  chapterCount: 1,
  sceneCount: 1,
  currentChars: 100
};

beforeEach(() => {
  vi.clearAllMocks();
  useCreationStore.setState({
    projects: [PROJECT as never],
    selectedId: "p1",
    navigations: {},
    outlines: {},
    cards: [],
    loading: false,
    watchConnected: false,
    projectNavigationRequests: {},
    inboxSelectionRequest: undefined,
    selectedSceneId: undefined,
    selectedCardId: undefined
  });
  useUIStore.setState({ toasts: [], confirmRequest: undefined });
  service.listProjects.mockResolvedValue([PROJECT]);
  service.readProjectHome.mockResolvedValue({ projects: [PROJECT] });
  service.inboxCount.mockResolvedValue({ total: 0, pending: 0 });
  service.migrationStatus.mockResolvedValue(null);
  service.readProjectNavigation.mockResolvedValue(null);
  operation.startOperation.mockResolvedValue(null);
  operation.subscribeOperation.mockReturnValue(vi.fn());
});
afterEach(cleanup);

function resolveConfirm(confirmed: boolean) {
  const pending = useUIStore.getState().confirmRequest;
  expect(pending).toBeTruthy();
  useUIStore.getState().resolveConfirm(confirmed);
}

describe("CreationProjectsPage 加密项目包入口", () => {
  it("工具栏出现加密导出/加密导入按钮", () => {
    render(<CreationProjectsPage />);
    expect(screen.getByRole("button", { name: "导出加密项目包" })).toBeTruthy();
    expect(screen.getByRole("button", { name: "导入加密项目包" })).toBeTruthy();
  });

  it("加密导出：口令合格后以选中项目启动 bundle.export-encrypted", async () => {
    render(<CreationProjectsPage />);
    fireEvent.click(screen.getByRole("button", { name: "导出加密项目包" }));
    fireEvent.change(screen.getByTestId("pe-passphrase"), { target: { value: "passphrase1" } });
    fireEvent.change(screen.getByTestId("pe-confirm"), { target: { value: "passphrase1" } });
    fireEvent.click(screen.getByTestId("pe-ack"));
    fireEvent.click(screen.getByTestId("pe-submit"));
    await waitFor(() =>
      expect(operation.startOperation).toHaveBeenCalledWith({
        kind: "bundle.export-encrypted",
        projectId: "p1",
        passphrase: "passphrase1"
      })
    );
    await waitFor(() => expect(screen.queryByTestId("passphrase-dialog")).toBeNull());
  });

  it("加密导入：先危险确认再输入口令，确认后启动 bundle.import-encrypted", async () => {
    render(<CreationProjectsPage />);
    fireEvent.click(screen.getByRole("button", { name: "导入加密项目包" }));
    await waitFor(() => expect(useUIStore.getState().confirmRequest?.title).toContain("导入加密项目包"));
    // 取消危险确认时不得进入口令输入流程（对话框保持关闭）。
    resolveConfirm(false);
    await waitFor(() => expect(useUIStore.getState().confirmRequest).toBeUndefined());
    expect(screen.queryByTestId("pe-submit")).toBeNull();

    fireEvent.click(screen.getByRole("button", { name: "导入加密项目包" }));
    await waitFor(() => expect(useUIStore.getState().confirmRequest).toBeTruthy());
    resolveConfirm(true);
    await waitFor(() => expect(screen.getByTestId("passphrase-dialog")).toBeTruthy());
    fireEvent.change(screen.getByTestId("pe-passphrase"), { target: { value: "opensesame" } });
    fireEvent.click(screen.getByTestId("pe-submit"));
    await waitFor(() =>
      expect(operation.startOperation).toHaveBeenCalledWith({ kind: "bundle.import-encrypted", passphrase: "opensesame" })
    );
  });

  it("启动失败时错误留在对话框内，用户可改口令重试", async () => {
    operation.startOperation.mockRejectedValueOnce(new Error("解密失败：口令不正确"));
    render(<CreationProjectsPage />);
    fireEvent.click(screen.getByRole("button", { name: "导入加密项目包" }));
    await waitFor(() => expect(useUIStore.getState().confirmRequest).toBeTruthy());
    resolveConfirm(true);
    await waitFor(() => expect(screen.getByTestId("passphrase-dialog")).toBeTruthy());
    fireEvent.change(screen.getByTestId("pe-passphrase"), { target: { value: "bad" } });
    fireEvent.click(screen.getByTestId("pe-submit"));
    await waitFor(() => expect(screen.getByTestId("pe-error").textContent).toContain("口令不正确"));
    expect(screen.getByTestId("passphrase-dialog")).toBeTruthy();
  });

  it("任务进行中（operation.isActive）时加密按钮禁用", async () => {
    // 产品语义：startOperation resolve 初始 running 状态后 useOperation 才进入运行态；
    // 订阅保持挂起（不推终态），使按钮持续禁用。
    operation.startOperation.mockImplementationOnce(() =>
      Promise.resolve({
        operationId: "op-running",
        kind: "bundle.export-encrypted",
        status: "running",
        progress: { phase: "copying", completed: 1, total: 2, bytesCompleted: 1, bytesTotal: 2, indeterminate: false },
        startedAt: "2026-09-28T00:00:00.000Z",
        deferredCancel: false,
        result: null
      })
    );
    operation.getOperationState.mockImplementation(() => new Promise(() => undefined));
    render(<CreationProjectsPage />);
    fireEvent.click(screen.getByRole("button", { name: "导出加密项目包" }));
    fireEvent.change(screen.getByTestId("pe-passphrase"), { target: { value: "passphrase1" } });
    fireEvent.change(screen.getByTestId("pe-confirm"), { target: { value: "passphrase1" } });
    fireEvent.click(screen.getByTestId("pe-ack"));
    fireEvent.click(screen.getByTestId("pe-submit"));
    await waitFor(() => expect((screen.getByRole("button", { name: "导入项目包" }) as HTMLButtonElement).disabled).toBe(true));
    expect((screen.getByRole("button", { name: "导入加密项目包" }) as HTMLButtonElement).disabled).toBe(true);
  });
});
