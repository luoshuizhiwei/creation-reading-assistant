import { useCallback } from "react";
import {
  cardExportOpenAndWrite,
  cardImportApply,
  cardImportOpenAndParse,
  cardImportParse,
  cardImportPlan,
  cardImportSchema,
  cardRead,
  cardRelations,
  cardsList,
  cardTypesList,
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
  CreationRunCommand
} from "@/types/creation";
import { executeAction, executeBoolAction } from "@/utils/async-action";
import { messageFromError } from "@/utils/format";

/**
 * loadCards 请求序列号：模块级共享，防止多个组件/项目并发加载时旧请求覆盖新结果。
 * 每个 loadCards 调用递增并捕获当前 seq；只有响应到达时 seq 仍匹配才写 store。
 */
let cardsLoadSeq = 0;
let cardsLoadLastProjectId = "";

export function useCardActions() {
  const setError = useAppStore((state) => state.setError);

  const loadCardTypes = useCallback(
    async (projectId: string): Promise<void> => {
      useCreationStore.getState().activateCardProject(projectId);
      await executeAction(
        async () => {
          useCreationStore.getState().setCardTypes(projectId, await cardTypesList(projectId));
        },
        { setError }
      );
    },
    [setError]
  );

  const loadRelationTypes = useCallback(
    async (projectId: string): Promise<void> => {
      useCreationStore.getState().activateCardProject(projectId);
      await executeAction(
        async () => {
          useCreationStore.getState().setRelationTypes(projectId, await relationTypesList(projectId));
        },
        { setError }
      );
    },
    [setError]
  );

  const loadCards = useCallback(
    async (query: Omit<CardsListQuery, "kind">): Promise<CardSummary[] | undefined> => {
      useCreationStore.getState().activateCardProject(query.projectId);
      useCreationStore.getState().setCardsLoading(query.projectId, true);
      const seq = ++cardsLoadSeq;
      cardsLoadLastProjectId = query.projectId;
      try {
        const result = await cardsList({ kind: "cards.list", ...query });
        if (seq === cardsLoadSeq && query.projectId === cardsLoadLastProjectId) {
          useCreationStore.getState().setCards(query.projectId, result);
          return result;
        }
        // 请求已过期：旧项目响应不得覆盖新项目，也不得被当成当前结果消费。
        return undefined;
      } catch (error) {
        setError(messageFromError(error));
        return undefined;
      } finally {
        if (seq === cardsLoadSeq && query.projectId === cardsLoadLastProjectId) {
          useCreationStore.getState().setCardsLoading(query.projectId, false);
        }
      }
    },
    [setError]
  );

  const loadCardRelations = useCallback(
    async (projectId: string, cardId: string): Promise<{ outgoing: CardRelation[]; incoming: CardRelation[] }> => {
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

  const readCard = useCallback(
    async (cardId: string) => {
      const res = await executeAction(() => cardRead(cardId), { setError });
      return res ?? null;
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
    loadCardRelations,
    readCard,
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
