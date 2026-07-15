import { CheckCircle2, Wifi } from "lucide-react";
import type { useMobileAISettings } from "../hooks/useMobileAISettings";
import type { ProfileSubPage } from "../ProfilePage";

export function AISettingsPage({
  ai,
  onSetActivePage
}: {
  ai: ReturnType<typeof useMobileAISettings>;
  onSetActivePage: (page: ProfileSubPage | undefined) => void;
}) {
  const { aiSettings, setAiSettings, aiKeyDraft, setAiKeyDraft, saveAiSettings, testMobileAI, clearAiKey } = ai;

  return (
    <div className="screen-stack profile-subpage">
      <header className="mobile-header row-header subpage-header">
        <button className="ghost-button back-button" onClick={() => onSetActivePage(undefined)}>
          ← 返回
        </button>
        <div>
          <p className="mini-label">工具</p>
          <h1>AI 助手</h1>
        </div>
      </header>
      <section className="subpage-card">
        <p className="subtle">手机端 AI Key 仅保存在应用本地沙箱。AI Key 不跨设备同步；这里配置 OpenAI-compatible 接口后，灵感中心可以直接生成候选版本，结果只进入 AI 候选，不覆盖原文。</p>
        <input value={aiSettings.baseUrl} onChange={(event) => setAiSettings((current) => ({ ...current, baseUrl: event.target.value }))} placeholder="https://dashscope.aliyuncs.com/compatible-mode/v1" />
        <input value={aiSettings.model} onChange={(event) => setAiSettings((current) => ({ ...current, model: event.target.value }))} placeholder="qwen-plus" />
        <label className="range-setting-row">
          <span>创造性 {aiSettings.temperature.toFixed(1)}</span>
          <input
            type="range"
            min="0"
            max="1.5"
            step="0.1"
            value={aiSettings.temperature}
            onChange={(event) => setAiSettings((current) => ({ ...current, temperature: Number(event.target.value) }))}
          />
        </label>
        <input
          type="password"
          value={aiKeyDraft}
          onChange={(event) => setAiKeyDraft(event.target.value)}
          placeholder={aiSettings.hasApiKey ? "已保存 API Key；留空则不修改" : "粘贴手机端 API Key"}
        />
        <div className="setting-check-row">
          <CheckCircle2 size={18} />
          <span>{aiSettings.hasApiKey ? "已保存 API Key；不会同步、导出或上传到 WebDAV。" : "还没有保存手机端 API Key。"}</span>
        </div>
        <div className="button-row settings-actions">
          <button onClick={() => void saveAiSettings()}>保存设置</button>
          <button onClick={() => void testMobileAI()}><Wifi size={17} />测试</button>
          <button className="secondary-button" onClick={() => void clearAiKey()}>清除 Key</button>
        </div>
      </section>
    </div>
  );
}
