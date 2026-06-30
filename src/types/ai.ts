import type { InspirationVariantKind } from "./inspiration";

export type AIProvider = "openai-compatible";
export type AIRunAction = InspirationVariantKind;

export interface AISettings {
  provider: AIProvider;
  baseUrl: string;
  model: string;
  temperature: number;
  hasApiKey: boolean;
}

export interface AISettingsPatch {
  provider?: AIProvider;
  baseUrl?: string;
  model?: string;
  temperature?: number;
}

export interface SaveAIApiKeyInput {
  apiKey: string;
}

export interface AIRunInput {
  action: AIRunAction;
  title?: string;
  content: string;
  platform?: string;
}

export interface AIRunResult {
  kind: AIRunAction;
  content: string;
  prompt: string;
  model: string;
}
