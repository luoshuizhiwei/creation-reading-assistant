import { useCallback } from "react";
import {
  addInspirationVariant,
  createInspiration,
  deleteInspiration,
  listInspirations,
  updateInspiration
} from "@/services/inspiration-service";
import { useInspirationStore } from "@/stores/inspiration-store";
import { useAppStore } from "@/stores/app-store";
import { executeAction } from "@/utils/async-action";
import type { AddInspirationVariantInput, CreateInspirationInput, UpdateInspirationInput } from "@/types/inspiration";

export function useInspirationActions() {
  const setItems = useInspirationStore((state) => state.setItems);
  const upsertItem = useInspirationStore((state) => state.upsertItem);
  const setLoading = useInspirationStore((state) => state.setLoading);
  const setError = useAppStore((state) => state.setError);

  const loadInspirations = useCallback(async () => {
    await executeAction(() => listInspirations(), {
      setLoading,
      setError,
      onSuccess: setItems
    });
  }, [setError, setItems, setLoading]);

  const createItem = useCallback(
    async (input: CreateInspirationInput) => {
      return await executeAction(() => createInspiration(input), {
        setLoading,
        setError,
        onSuccess: upsertItem
      });
    },
    [setError, setLoading, upsertItem]
  );

  const updateItem = useCallback(
    async (id: string, input: UpdateInspirationInput) => {
      return await executeAction(() => updateInspiration(id, input), {
        setLoading,
        setError,
        onSuccess: upsertItem
      });
    },
    [setError, setLoading, upsertItem]
  );

  const deleteItem = useCallback(
    async (id: string) => {
      const result = await executeAction(() => deleteInspiration(id), {
        setLoading,
        setError,
        onSuccess: setItems
      });
      return result !== undefined;
    },
    [setError, setItems, setLoading]
  );

  const addVariant = useCallback(
    async (id: string, input: AddInspirationVariantInput) => {
      return await executeAction(() => addInspirationVariant(id, input), {
        setLoading,
        setError,
        onSuccess: upsertItem
      });
    },
    [setError, setLoading, upsertItem]
  );

  return { loadInspirations, createItem, updateItem, deleteItem, addVariant };
}
