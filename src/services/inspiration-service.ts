import { getDesktopApi } from "@/services/ipc-client";
import type { AddInspirationVariantInput, CreateInspirationInput, InspirationItem, UpdateInspirationInput } from "@/types/inspiration";

export async function listInspirations(): Promise<InspirationItem[]> {
  return getDesktopApi().inspiration.list();
}

export async function createInspiration(input: CreateInspirationInput): Promise<InspirationItem> {
  return getDesktopApi().inspiration.create(input);
}

export async function readInspiration(id: string): Promise<InspirationItem | undefined> {
  return getDesktopApi().inspiration.read(id);
}

export async function updateInspiration(id: string, input: UpdateInspirationInput): Promise<InspirationItem> {
  return getDesktopApi().inspiration.update(id, input);
}

export async function deleteInspiration(id: string): Promise<InspirationItem[]> {
  return getDesktopApi().inspiration.delete(id);
}

export async function addInspirationVariant(id: string, input: AddInspirationVariantInput): Promise<InspirationItem> {
  return getDesktopApi().inspiration.addVariant(id, input);
}
