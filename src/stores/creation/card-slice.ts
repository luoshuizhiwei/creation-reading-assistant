import type { StateCreator } from "zustand";
import type { CardLinkResult, CardRelation, CardSummary, CardType, RelationType } from "@/types/creation";

export interface CardSliceState {
  cardTypes: CardType[];
  relationTypes: RelationType[];
  cards: CardSummary[];
  /** null 表示全局卡片库；字符串表示项目关联视图。 */
  cardProjectId: string | null | undefined;
  cardRelations: Record<string, { outgoing: CardRelation[]; incoming: CardRelation[] }>;
  selectedCardId?: string;
  cardsLoading: boolean;
}

export interface CardSliceActions {
  activateCardProject: (projectId: string) => void;
  activateGlobalCards: () => void;
  setCardTypes: (cardTypes: CardType[]) => void;
  setRelationTypes: (relationTypes: RelationType[]) => void;
  setCards: (projectId: string | null, cards: CardSummary[]) => void;
  setCardRelations: (
    projectId: string | null,
    cardId: string,
    relations: { outgoing: CardRelation[]; incoming: CardRelation[] }
  ) => void;
  selectCard: (cardId?: string) => void;
  setCardsLoading: (projectId: string | null, cardsLoading: boolean) => void;
  applyCardLinkResult: (result: CardLinkResult) => void;
}

export type CardSlice = CardSliceState & CardSliceActions;

export const initialCardState: CardSliceState = {
  cardTypes: [],
  relationTypes: [],
  cards: [],
  cardProjectId: undefined,
  cardRelations: {},
  selectedCardId: undefined,
  cardsLoading: false
};

// eslint-disable-next-line @typescript-eslint/no-explicit-any
export const createCardSlice: StateCreator<any, [], [], CardSlice> = (set) => ({
  ...initialCardState,
  activateCardProject: (projectId) =>
    set((state: CardSliceState) =>
      state.cardProjectId === projectId
        ? state
        : {
            cardProjectId: projectId,
            cards: [],
            cardRelations: {},
            selectedCardId: undefined,
            cardsLoading: false
          }
    ),
  activateGlobalCards: () =>
    set((state: CardSliceState) =>
      state.cardProjectId === null
        ? state
        : {
            cardProjectId: null,
            cards: [],
            cardRelations: {},
            selectedCardId: undefined,
            cardsLoading: false
          }
    ),
  setCardTypes: (cardTypes) => set({ cardTypes }),
  setRelationTypes: (relationTypes) => set({ relationTypes }),
  setCards: (projectId, cards) =>
    set((state: CardSliceState) => (state.cardProjectId === projectId ? { cards } : state)),
  setCardRelations: (projectId, cardId, relations) =>
    set((state: CardSliceState) =>
      state.cardProjectId === projectId
        ? { cardRelations: { ...state.cardRelations, [cardId]: relations } }
        : state
    ),
  selectCard: (selectedCardId) => set({ selectedCardId }),
  setCardsLoading: (projectId, cardsLoading) =>
    set((state: CardSliceState) => (state.cardProjectId === projectId ? { cardsLoading } : state)),
  applyCardLinkResult: (result) =>
    set((state: CardSliceState) => ({
      cards: state.cards
        .map((card) => {
          if (card.id !== result.cardId) return card;
          const linkedProjectIds = result.linked
            ? [...new Set([...card.linkedProjectIds, result.projectId])].sort()
            : card.linkedProjectIds.filter((projectId) => projectId !== result.projectId);
          return { ...card, linkedProjectIds, usageCount: result.usageCount };
        })
        .filter((card) => state.cardProjectId === null || card.id !== result.cardId || result.linked)
    }))
});
