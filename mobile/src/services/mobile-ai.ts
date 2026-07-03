import type { AIRunAction, AIRunInput, AIRunResult } from "../../../src/types/ai";
import { deleteMobileSecret, readMobileSecret, writeMobileSecret } from "./mobile-secret-store";

const AI_BASE_URL_KEY = "creation-reading-assistant-mobile-ai-base-url";
const AI_MODEL_KEY = "creation-reading-assistant-mobile-ai-model";
const AI_TEMPERATURE_KEY = "creation-reading-assistant-mobile-ai-temperature";
const AI_HAS_API_KEY_KEY = "creation-reading-assistant-mobile-ai-has-api-key";
const LEGACY_AI_API_KEY_KEY = "creation-reading-assistant-mobile-ai-api-key";
const AI_SECRET_RECORD_ID = "mobile-ai-api-key";

export interface MobileAISettings {
  baseUrl: string;
  model: string;
  temperature: number;
  hasApiKey: boolean;
}

export function loadMobileAISettings(): MobileAISettings {
  const temperature = Number(localStorage.getItem(AI_TEMPERATURE_KEY) ?? "0.7");
  return {
    baseUrl: localStorage.getItem(AI_BASE_URL_KEY) ?? "",
    model: localStorage.getItem(AI_MODEL_KEY) ?? "qwen-plus",
    temperature: Number.isFinite(temperature) ? temperature : 0.7,
    hasApiKey: localStorage.getItem(AI_HAS_API_KEY_KEY) === "true" || Boolean(localStorage.getItem(LEGACY_AI_API_KEY_KEY))
  };
}

async function loadMobileAIApiKey(): Promise<string | undefined> {
  const current = await readMobileSecret(AI_SECRET_RECORD_ID);
  if (current) return current;
  const legacy = localStorage.getItem(LEGACY_AI_API_KEY_KEY)?.trim();
  if (!legacy) return undefined;
  await writeMobileSecret(AI_SECRET_RECORD_ID, legacy);
  localStorage.removeItem(LEGACY_AI_API_KEY_KEY);
  localStorage.setItem(AI_HAS_API_KEY_KEY, "true");
  return legacy;
}

export async function saveMobileAISettings(input: {
  baseUrl: string;
  model: string;
  temperature?: number;
  apiKey?: string;
}): Promise<MobileAISettings> {
  const baseUrl = input.baseUrl.trim();
  const model = input.model.trim() || "qwen-plus";
  const temperature = Math.min(1.5, Math.max(0, input.temperature ?? 0.7));
  localStorage.setItem(AI_BASE_URL_KEY, baseUrl);
  localStorage.setItem(AI_MODEL_KEY, model);
  localStorage.setItem(AI_TEMPERATURE_KEY, String(temperature));
  const apiKey = input.apiKey?.trim();
  if (apiKey) {
    await writeMobileSecret(AI_SECRET_RECORD_ID, apiKey);
    localStorage.removeItem(LEGACY_AI_API_KEY_KEY);
    localStorage.setItem(AI_HAS_API_KEY_KEY, "true");
  }
  return loadMobileAISettings();
}

export async function clearMobileAIApiKey(): Promise<MobileAISettings> {
  await deleteMobileSecret(AI_SECRET_RECORD_ID);
  localStorage.removeItem(LEGACY_AI_API_KEY_KEY);
  localStorage.removeItem(AI_HAS_API_KEY_KEY);
  return loadMobileAISettings();
}

function actionLabel(action: AIRunAction): string {
  switch (action) {
    case "polish":
      return "润色";
    case "expand":
      return "扩写";
    case "platform-style":
      return "平台风格化";
    case "conflict":
      return "生成冲突点";
    case "humanize":
      return "去 AI 味";
    default:
      return "处理";
  }
}

function buildPrompt(input: AIRunInput): string {
  const title = input.title ? `标题：${input.title}\n` : "";
  const platform = input.platform ? `目标平台：${input.platform}\n` : "";
  const instruction: Record<AIRunAction, string> = {
    polish: "把这条小说灵感润色成更清楚、更有画面感、更适合后续写作的素材。保留原意，不要替作者决定完整剧情。",
    expand: "基于这条小说灵感扩展 3-5 个可写方向，包括人物动机、场景推进和可用细节。",
    "platform-style": "把这条灵感改写成更适合中文网文平台使用的素材，节奏直接、冲突清楚、表达自然。",
    conflict: "从这条灵感中提炼或生成 5 个可推动剧情的冲突点，每个冲突点给一句可写切入。",
    humanize: "去掉机械总结感和 AI 腔，把这条灵感改成更自然、更像作者自己随手记录但清楚可用的素材。"
  };
  return `${instruction[input.action]}\n\n${title}${platform}原始灵感：\n${input.content}\n\n要求：只输出候选内容，不要解释你做了什么。`;
}

export async function runMobileAIAction(input: AIRunInput): Promise<AIRunResult> {
  const settings = loadMobileAISettings();
  const apiKey = await loadMobileAIApiKey();
  if (!settings.baseUrl.trim()) throw new Error("请先在“我的 / AI 助手”填写 Base URL。");
  if (!settings.model.trim()) throw new Error("请先在“我的 / AI 助手”填写模型名。");
  if (!apiKey) throw new Error("请先在“我的 / AI 助手”保存手机端 API Key。");
  const prompt = buildPrompt(input);
  const endpoint = `${settings.baseUrl.replace(/\/+$/, "")}/chat/completions`;
  const response = await fetch(endpoint, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Authorization: `Bearer ${apiKey}`
    },
    body: JSON.stringify({
      model: settings.model,
      temperature: settings.temperature,
      messages: [
        {
          role: "system",
          content: "你是中文小说作者的灵感打磨助手。输出要自然、具体、可继续写，不要有 AI 腔。"
        },
        { role: "user", content: prompt }
      ]
    })
  });
  if (!response.ok) {
    const detail = await response.text().catch(() => "");
    throw new Error(`AI ${actionLabel(input.action)}失败：${response.status} ${response.statusText}${detail ? "。请检查 Base URL、模型名、Key 或额度。" : ""}`);
  }
  const payload = (await response.json()) as { choices?: Array<{ message?: { content?: string } }> };
  const content = payload.choices?.[0]?.message?.content?.trim();
  if (!content) throw new Error("AI 返回为空，请稍后重试或换一个模型。");
  return {
    kind: input.action,
    content,
    prompt,
    model: settings.model
  };
}
