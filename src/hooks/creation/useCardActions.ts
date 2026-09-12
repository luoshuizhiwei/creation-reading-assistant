import { useCallback } from "react";
import {
  cardExportOpenAndWrite,
  cardImportApply,
  cardImportOpenAndParse,
  cardImportParse,
  cardImportPlan,
  cardImportSchema,
  cardRead,
  cardLink,
  cardUnlink,
  cardRelations,
  cardsList,
  cardTypesList,
  relationGraph,
  relationTypesList,
  runStructure as runStructureRequest
} from "@/services/creation-service";
import { useAppStore } from "@/stores/app-store";
import { useCreationStore } from "@/stores/creation-store";
import type {
  CardExportFilter,
  CardExportResult,
  CardImportApplyInput,
  CardImportApplyResult,
  CardImportPlan,
  CardImportPreview,
  CardImportSchemaContext,
  CardImportSource
} from "@/types/card-io";
import type {
  CardRelation,
  CardsListQuery,
  CardSummary,
  CreationRunCommand,
  RelationGraphQuery,
  RelationGraphView
} from "@/types/creation";
import { executeAction, executeBoolAction } from "@/utils/async-action";
import { messageFromError } from "@/utils/format";

/**
 * loadCards 请求序列号：模块级共享，防止多个组件/项目并发加载时旧请求覆盖新结果。
 * 每个 loadCards 调用递增并捕获当前 seq；只有响应到达时 seq 仍匹配才写 store。
 */
let cardsLoadSeq = 0;
let cardsLoadLastScope: string | null = null;
let cardTypesLoadSeq = 0;
let relationTypesLoadSeq = 0;

export function useCardActions() {
  const setError = useAppStore((state) => state.setError);

  const loadCardTypes = useCallback(
    async (_projectId?: string): Promise<void> => {
      const seq = ++cardTypesLoadSeq;
      await executeAction(
        async () => {
          const result = await cardTypesList();
          if (seq === cardTypesLoadSeq) useCreationStore.getState().setCardTypes(result);
        },
        { setError }
      );
    },
    [setError]
  );

  const loadRelationTypes = useCallback(
    async (_projectId?: string): Promise<void> => {
      const seq = ++relationTypesLoadSeq;
      await executeAction(
        async () => {
          const result = await relationTypesList();
          if (seq === relationTypesLoadSeq) useCreationStore.getState().setRelationTypes(result);
        },
        { setError }
      );
    },
    [setError]
  );

  const loadCards = useCallback(
    async (query: Omit<CardsListQuery, "kind">): Promise<CardSummary[] | undefined> => {
      const scope = query.projectId ?? null;
      if (query.projectId) useCreationStore.getState().activateCardProject(query.projectId);
      else useCreationStore.getState().activateGlobalCards();
      useCreationStore.getState().setCardsLoading(scope, true);
      const seq = ++cardsLoadSeq;
      cardsLoadLastScope = scope;
      try {
        const result = await cardsList({ kind: "cards.list", ...query });
        if (seq === cardsLoadSeq && scope === cardsLoadLastScope) {
          useCreationStore.getState().setCards(scope, result);
          return result;
        }
        // 请求已过期：旧项目响应不得覆盖新项目，也不得被当成当前结果消费。
        return undefined;
      } catch (error) {
        setError(messageFromError(error));
        return undefined;
      } finally {
        if (seq === cardsLoadSeq && scope === cardsLoadLastScope) {
          useCreationStore.getState().setCardsLoading(scope, false);
        }
      }
    },
    [setError]
  );

  /**
   * 用于“关联全局卡片”选择器的只读查询。
   * 不能复用 loadCards：它会切换 cards store 的当前作用域，令项目页被全局结果覆盖。
   */
  const listGlobalCards = useCallback(
    async (query: Omit<CardsListQuery, "kind" | "projectId"> = {}): Promise<CardSummary[]> => {
      const result = await executeAction(() => cardsList({ kind: "cards.list", ...query }), { setError });
      return result ?? [];
    },
    [setError]
  );

  const loadCardRelations = useCallback(
    async (projectId: string | null, cardId: string): Promise<{ outgoing: CardRelation[]; incoming: CardRelation[] }> => {
      const res = await executeAction(
        async () => {
          const relations = await cardRelations(cardId);
          useCreationStore.getState().setCardRelations(projectId, cardId, relations);
          return relations;
        },
        { setError }
      );
      return res ?? { outgoing: [], incoming: [] };
    },
    [setError]
  );

  /** 关系图整图（Stage 4-F）：一次取回节点与连线，不写入 store（图是只读投影）。 */
  const loadRelationGraph = useCallback(
    async (query: RelationGraphQuery): Promise<RelationGraphView | null> => {
      const res = await executeAction(() => relationGraph(query), { setError });
      return res ?? null;
    },
    [setError]
  );

  const readCard = useCallback(
    async (cardId: string) => {
      const res = await executeAction(() => cardRead(cardId), { setError });
      return res ?? null;
    },
    [setError]
  );

  const linkCardToProject = useCallback(
    async (projectId: string, cardId: string) => {
      const result = await executeAction(() => cardLink(projectId, cardId), { setError });
      if (result) useCreationStore.getState().applyCardLinkResult(result);
      return result ?? null;
    },
    [setError]
  );

  const unlinkCardFromProject = useCallback(
    async (projectId: string, cardId: string) => {
      const result = await executeAction(() => cardUnlink(projectId, cardId), { setError });
      if (result) useCreationStore.getState().applyCardLinkResult(result);
      return result ?? null;
    },
    [setError]
  );

  const updateCardType = useCallback(
    async (command: Extract<CreationRunCommand, { type: "cardType.update" }>): Promise<boolean> =>
      executeBoolAction(() => runStructureRequest(command), { setError }),
    [setError]
  );

  const deleteCardType = useCallback(
    async (command: Extract<CreationRunCommand, { type: "cardType.delete" }>): Promise<boolean> =>
      executeBoolAction(() => runStructureRequest(command), { setError }),
    [setError]
  );

  const updateRelationType = useCallback(
    async (command: Extract<CreationRunCommand, { type: "relationType.update" }>): Promise<boolean> =>
      executeBoolAction(() => runStructureRequest(command), { setError }),
    [setError]
  );

  const deleteRelationType = useCallback(
    async (command: Extract<CreationRunCommand, { type: "relationType.delete" }>): Promise<boolean> =>
      executeBoolAction(() => runStructureRequest(command), { setError }),
    [setError]
  );

  const openCardImport = useCallback(async (): Promise<CardImportSource | null> => {
    const res = await executeAction(() => cardImportOpenAndParse(), { setError });
    return res ?? null;
  }, [setError]);

  const parseCardImport = useCallback(
    async (input: { text: string; format: "csv" | "markdown" }): Promise<CardImportPreview | null> => {
      const res = await executeAction(() => cardImportParse(input), { setError });
      return res ?? null;
    },
    [setError]
  );

  const loadCardImportSchema = useCallback(
    async (projectId: string): Promise<CardImportSchemaContext | null> => {
      const res = await executeAction(() => cardImportSchema(projectId), { setError });
      return res ?? null;
    },
    [setError]
  );

  const planCardImport = useCallback(
    async (input: CardImportApplyInput): Promise<CardImportPlan | null> => {
      const res = await executeAction(() => cardImportPlan(input), { setError });
      return res ?? null;
    },
    [setError]
  );

  const applyCardImport = useCallback(
    async (input: CardImportApplyInput): Promise<CardImportApplyResult | null> => {
      const res = await executeAction(() => cardImportApply(input), { setError });
      return res ?? null;
    },
    [setError]
  );

  const exportCards = useCallback(
    async (input: {
      projectId: string;
      filter: CardExportFilter;
      format: "csv" | "markdown";
    }): Promise<CardExportResult | null> => {
      const res = await executeAction(() => cardExportOpenAndWrite(input), { setError });
      return res ?? null;
    },
    [setError]
  );

  return {
    loadCardTypes,
    loadRelationTypes,
    loadCards,
    listGlobalCards,
    loadCardRelations,
    loadRelationGraph,
    readCard,
    linkCardToProject,
    unlinkCardFromProject,
    updateCardType,
    deleteCardType,
    updateRelationType,
    deleteRelationType,
    openCardImport,
    parseCardImport,
    loadCardImportSchema,
    planCardImport,
    applyCardImport,
    exportCards
  };
}
