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
import type { AddInspirationVariantInput, CreateInspirationInput, UpdateInspirationInput } from "@/types/inspiration";

function messageFromError(error: unknown): string {
  return error instanceof Error ? error.message : String(error);
}

export function useInspirationActions() {
  const setItems = useInspirationStore((state) => state.setItems);
  const upsertItem = useInspirationStore((state) => state.upsertItem);
  const setLoading = useInspirationStore((state) => state.setLoading);
  const setError = useAppStore((state) => state.setError);

  const loadInspirations = useCallback(async () => {
    setLoading(true);
    try {
      setItems(await listInspirations());
    } catch (error) {
      setError(messageFromError(error));
    } finally {
      setLoading(false);
    }
  }, [setError, setItems, setLoading]);

  const createItem = useCallback(
    async (input: CreateInspirationInput) => {
      setLoading(true);
      try {
        const item = await createInspiration(input);
        upsertItem(item);
        return item;
      } catch (error) {
        setError(messageFromError(error));
        return undefined;
      } finally {
        setLoading(false);
      }
    },
    [setError, setLoading, upsertItem]
  );

  const updateItem = useCallback(
    async (id: string, input: UpdateInspirationInput) => {
      setLoading(true);
      try {
        const item = await updateInspiration(id, input);
        upsertItem(item);
        return item;
      } catch (error) {
        setError(messageFromError(error));
        return undefined;
      } finally {
        setLoading(false);
      }
    },
    [setError, setLoading, upsertItem]
  );

  const deleteItem = useCallback(
    async (id: string) => {
      setLoading(true);
      try {
        setItems(await deleteInspiration(id));
        return true;
      } catch (error) {
        setError(messageFromError(error));
        return false;
      } finally {
        setLoading(false);
      }
    },
    [setError, setItems, setLoading]
  );

  const addVariant = useCallback(
    async (id: string, input: AddInspirationVariantInput) => {
      setLoading(true);
      try {
        const item = await addInspirationVariant(id, input);
        upsertItem(item);
        return item;
      } catch (error) {
        setError(messageFromError(error));
        return undefined;
      } finally {
        setLoading(false);
      }
    },
    [setError, setLoading, upsertItem]
  );

  return { loadInspirations, createItem, updateItem, deleteItem, addVariant };
}

