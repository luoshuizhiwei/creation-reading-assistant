import { getDesktopApi } from "@/services/ipc-client";
import type { AISettings, AISettingsPatch, AIRunInput, AIRunResult, SaveAIApiKeyInput } from "@/types/ai";

export async function getAISettings(): Promise<AISettings> {
  return getDesktopApi().ai.getSettings();
}

export async function updateAISettings(patch: AISettingsPatch): Promise<AISettings> {
  return getDesktopApi().ai.updateSettings(patch);
}

export async function saveAIApiKey(input: SaveAIApiKeyInput): Promise<AISettings> {
  return getDesktopApi().ai.saveApiKey(input);
}

export async function clearAIApiKey(): Promise<AISettings> {
  return getDesktopApi().ai.clearApiKey();
}

export async function testAIConnection(): Promise<{ ok: boolean; message: string }> {
  return getDesktopApi().ai.test();
}

export async function runAIAction(input: AIRunInput): Promise<AIRunResult> {
  return getDesktopApi().ai.run(input);
}
