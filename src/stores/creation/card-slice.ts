import type { StateCreator } from "zustand";
import type { CardRelation, CardSummary, CardType, RelationType } from "@/types/creation";

export interface CardSliceState {
  cardTypes: CardType[];
  relationTypes: RelationType[];
  cards: CardSummary[];
  cardProjectId?: string;
  cardRelations: Record<string, { outgoing: CardRelation[]; incoming: CardRelation[] }>;
  selectedCardId?: string;
  cardsLoading: boolean;
}

export interface CardSliceActions {
  activateCardProject: (projectId: string) => void;
  setCardTypes: (projectId: string, cardTypes: CardType[]) => void;
  setRelationTypes: (projectId: string, relationTypes: RelationType[]) => void;
  setCards: (projectId: string, cards: CardSummary[]) => void;
  setCardRelations: (
    projectId: string,
    cardId: string,
    relations: { outgoing: CardRelation[]; incoming: CardRelation[] }
  ) => void;
  selectCard: (cardId?: string) => void;
  setCardsLoading: (projectId: string, cardsLoading: boolean) => void;
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
            cardTypes: [],
            relationTypes: [],
            cards: [],
            cardRelations: {},
            selectedCardId: undefined,
            cardsLoading: false
          }
    ),
  setCardTypes: (projectId, cardTypes) =>
    set((state: CardSliceState) => (state.cardProjectId === projectId ? { cardTypes } : state)),
  setRelationTypes: (projectId, relationTypes) =>
    set((state: CardSliceState) => (state.cardProjectId === projectId ? { relationTypes } : state)),
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
    set((state: CardSliceState) => (state.cardProjectId === projectId ? { cardsLoading } : state))
});
