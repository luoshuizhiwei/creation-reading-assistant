import { useState } from "react";
import {
  clearMobileAIApiKey,
  loadMobileAISettings,
  runMobileAIAction,
  saveMobileAISettings
} from "../../../services/mobile-ai";

export function useMobileAISettings({ onMessage }: { onMessage: (value: string) => void }) {
  const [aiSettings, setAiSettings] = useState(() => loadMobileAISettings());
  const [aiKeyDraft, setAiKeyDraft] = useState("");

  const saveAiSettings = async () => {
    const next = await saveMobileAISettings({ ...aiSettings, apiKey: aiKeyDraft });
    setAiSettings(next);
    setAiKeyDraft("");
    onMessage("已保存手机端 AI 连接信息。API Key 仅保存在应用本地沙箱，不跨设备同步，灵感中心可直接生成候选版本。");
  };

  const clearAiKey = async () => {
    const next = await clearMobileAIApiKey();
    setAiSettings(next);
    setAiKeyDraft("");
    onMessage("已清除手机端 AI API Key。");
  };

  const testMobileAI = async () => {
    try {
      await saveAiSettings();
      const result = await runMobileAIAction({
        action: "polish",
        title: "测试连接",
        content: "一个角色在雨夜想起旧约定。"
      });
      onMessage(result.content ? "AI 连接可用，灵感中心可以生成候选版本。" : "AI 返回为空。");
    } catch (error) {
      onMessage(error instanceof Error ? error.message : String(error));
    }
  };

  return { aiSettings, setAiSettings, aiKeyDraft, setAiKeyDraft, saveAiSettings, clearAiKey, testMobileAI };
}
