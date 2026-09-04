import { useState } from "react";
import { InlineNotice } from "@/components/interaction";
import { Button, Field, Select, TextInput } from "@/components/ui";
import { clearAIApiKey, saveAIApiKey, testAIConnection, updateAISettings } from "@/services/ai-service";
import type { AppSettings, AppSettingsPatch, SettingsSection } from "@/types/settings";
import { Section, patchNumericSetting } from "./SectionWrapper";

interface AISectionProps {
  settings: AppSettings;
  patchSettings: (patch: AppSettingsPatch) => Promise<unknown>;
  loadSettings: () => Promise<AppSettings | undefined>;
  resetSection: (section: SettingsSection) => void;
  showToast: (toast: { tone: "success" | "info" | "warning" | "error"; title: string; body?: string }) => void;
  setError: (error: string) => void;
}

export function AISection({
  settings,
  patchSettings,
  loadSettings,
  resetSection,
  showToast,
  setError
}: AISectionProps) {
  const [apiKey, setApiKey] = useState("");
  const [aiMessage, setAiMessage] = useState("");

  const refreshAISettings = async () => {
    await updateAISettings({});
    await loadSettings();
  };

  const saveKey = async () => {
    setAiMessage("正在保存 API Key...");
    try {
      const next = await saveAIApiKey({ apiKey });
      setApiKey("");
      await patchSettings({ ai: next });
      setAiMessage("API Key 已加密保存。");
      showToast({ tone: "success", title: "API Key 已保存", body: "密钥已由主进程加密保存，前端不会读取明文。" });
    } catch (error) {
      setAiMessage("");
      setError(error instanceof Error ? error.message : String(error));
    }
  };

  const clearKey = async () => {
    setAiMessage("正在清除 API Key...");
    try {
      const next = await clearAIApiKey();
      await patchSettings({ ai: next });
      setAiMessage("API Key 已清除。");
      showToast({ tone: "success", title: "API Key 已清除" });
    } catch (error) {
      setAiMessage("");
      setError(error instanceof Error ? error.message : String(error));
    }
  };

  const testAI = async () => {
    setAiMessage("正在测试 AI 连接...");
    try {
      const result = await testAIConnection();
      await refreshAISettings();
      setAiMessage(result.message);
      showToast({ tone: "success", title: "AI 连接正常", body: result.message });
    } catch (error) {
      setAiMessage("");
      setError(error instanceof Error ? error.message : String(error));
    }
  };

  return (
    <Section title="AI 助手" section="ai" onReset={resetSection}>
      <label className="col-span-2 flex items-center gap-2 text-sm text-paper-muted">
        <input
          type="checkbox"
          checked={settings.ai.enabled}
          onChange={(event) => void patchSettings({ ai: { enabled: event.target.checked } })}
        />
        启用 AI 助手（关闭时不会向任何服务发送内容，也不会出现 AI 操作）
      </label>
      {!settings.ai.enabled && (
        <div className="col-span-2 rounded-md border border-paper-line bg-paper-soft/45 p-3 text-xs leading-6 text-paper-muted">
          AI 助手已关闭。开启后可用「润色 / 扩写 / 平台风格化」等本地灵感打磨，但开启前请先配置 API Key。
        </div>
      )}
      {settings.ai.enabled && !settings.ai.hasApiKey && (
        <InlineNotice tone="warning" className="col-span-2 p-2 text-xs">
          已启用 AI 助手，但尚未配置 API Key。请先在下方保存 API Key 才能使用 AI 打磨；在此之前不会产生任何网络请求。
        </InlineNotice>
      )}
      {settings.ai.enabled && (
        <div className="col-span-2 rounded-md border border-amber-200 bg-amber-50/60 p-3 text-xs leading-6 text-paper-muted">
          开启 AI 后，你输入的正文、灵感标题与平台标签会发送到你配置的 AI 服务（Base URL）。API Key 仅在主进程加密保存，前端不读取明文。
        </div>
      )}
      <Select
        label="Provider"
        value={settings.ai.provider}
        onChange={(event) => void patchSettings({ ai: { provider: event.target.value as typeof settings.ai.provider } })}
        options={[
          { value: "openai-compatible", label: "OpenAI-compatible" }
        ]}
      />
      <Field label="Base URL">
        <TextInput
          value={settings.ai.baseUrl}
          onChange={(event) => void patchSettings({ ai: { baseUrl: event.target.value } })}
          placeholder="https://api.openai.com/v1"
        />
      </Field>
      <Field label="模型名">
        <TextInput
          value={settings.ai.model}
          onChange={(event) => void patchSettings({ ai: { model: event.target.value } })}
          placeholder="gpt-4.1-mini"
        />
      </Field>
      <Field label="Temperature">
        <TextInput
          type="number"
          min={0}
          max={1.5}
          step={0.1}
          value={settings.ai.temperature}
          onChange={(event) =>
            patchNumericSetting(event.target.value, 0, 1.5, (temperature) => ({ ai: { temperature } }), patchSettings)
          }
        />
      </Field>
      <Field label={settings.ai.hasApiKey ? "API Key（已保存，可留空）" : "API Key"}>
        <TextInput
          type="password"
          value={apiKey}
          onChange={(event) => setApiKey(event.target.value)}
          placeholder={settings.ai.hasApiKey ? "输入新 Key 可替换现有配置" : "sk-..."}
        />
      </Field>
      <div className="grid gap-2">
        <div className="text-xs text-paper-muted">{settings.ai.hasApiKey ? "状态：API Key 已加密保存" : "状态：尚未配置 API Key"}</div>
        <div className="flex flex-wrap gap-2">
          <Button variant="secondary" disabled={!apiKey.trim()} onClick={() => void saveKey()}>
            保存 API Key
          </Button>
          <Button variant="secondary" disabled={!settings.ai.hasApiKey} onClick={() => void clearKey()}>
            清除 Key
          </Button>
          <Button disabled={!settings.ai.hasApiKey} onClick={() => void testAI()}>
            测试连接
          </Button>
        </div>
        {aiMessage && (
          <InlineNotice tone="info" className="p-2 text-xs">
            {aiMessage}
          </InlineNotice>
        )}
      </div>
    </Section>
  );
}
