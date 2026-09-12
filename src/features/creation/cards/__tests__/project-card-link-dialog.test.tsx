// @vitest-environment jsdom
import React from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { ProjectCardLinkDialog } from "@/features/creation/cards/ProjectCardLinkDialog";
import * as creationService from "@/services/creation-service";
import { useAppStore } from "@/stores/app-store";
import type { CardSummary, CardType } from "@/types/creation";

vi.mock("@/services/creation-service", () => ({ cardsList: vi.fn() }));

const type: CardType = { id: "character", projectId: null, kind: "character", name: "角色", fields: [], sortOrder: 0, createdAt: "", updatedAt: "", revision: 1 };
const card = (id: string, linkedProjectIds: string[] = []): CardSummary => ({
  id, projectId: null, linkedProjectIds, usageCount: linkedProjectIds.length, kind: "character", title: id,
  aliases: [], fields: {}, tags: [], createdAt: "", updatedAt: "", revision: 1
});

beforeEach(() => {
  vi.mocked(creationService.cardsList).mockReset();
  useAppStore.setState({ error: undefined });
});
afterEach(cleanup);

describe("ProjectCardLinkDialog", () => {
  it("只读查询全局库，已关联卡片不可重复关联", async () => {
    vi.mocked(creationService.cardsList).mockResolvedValue([card("already", ["project-a"]), card("available")]);
    const onLink = vi.fn().mockResolvedValue(true);
    render(<ProjectCardLinkDialog projectId="project-a" linkedCardIds={["already"]} cardTypes={[type]} onClose={vi.fn()} onLink={onLink} />);

    await waitFor(() => expect(screen.getByText("available")).toBeTruthy());
    expect(creationService.cardsList).toHaveBeenCalledWith({ kind: "cards.list", cardKind: undefined, search: undefined });
    expect(screen.getByText("已关联")).toBeTruthy();
    fireEvent.click(screen.getByRole("button", { name: "关联到当前项目" }));
    await waitFor(() => expect(onLink).toHaveBeenCalledWith("available"));
    expect(screen.getAllByText("已关联")).toHaveLength(2);
  });

  it("按搜索文本重新查询全局库", async () => {
    vi.mocked(creationService.cardsList).mockResolvedValue([]);
    render(<ProjectCardLinkDialog projectId="project-a" linkedCardIds={[]} cardTypes={[type]} onClose={vi.fn()} onLink={vi.fn()} />);
    await waitFor(() => expect(creationService.cardsList).toHaveBeenCalledTimes(1));
    fireEvent.change(screen.getByLabelText("搜索全局卡片"), { target: { value: "星港" } });
    await waitFor(() => expect(creationService.cardsList).toHaveBeenLastCalledWith({ kind: "cards.list", cardKind: undefined, search: "星港" }));
  });
});
