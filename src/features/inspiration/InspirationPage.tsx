import { Inbox as InboxIcon, Lightbulb } from "lucide-react";
import { Button } from "@/components/ui";
import { useAppStore } from "@/stores/app-store";

/**
 * 兼容层（旧灵感中心）。
 *
 * 旧灵感数据已通过现有迁移并入「全局收件箱」，收件箱是唯一的想法收集入口。
 * 本页不再承担长期独立的灵感管理职责，仅作为兼容说明 + 安全跳转到收件箱，
 * 避免旧入口（阅读页「收集灵感」、导航等）直接失效。旧 inspirations.json 文件保留不删。
 * 最终的入口隐藏 / 路由收口由集成 Agent 统一处理。
 */
export function InspirationPage() {
  const setScreen = useAppStore((state) => state.setScreen);

  return (
    <div className="desktop-inspiration-page paper-shell">
      {/* 批次 G：删掉 rounded-2xl——面板圆角由 editorial-studio.css 的家族规则
          统一给（同特异度 + !important），写在 className 上画不出来，留在这里只会
          让下一个人以为自己改动了圆角。
          批次 AJ：再删掉 border border-paper-line 与 shadow-paper，同一个理由——
          家族规则归入 --shadow-1 之后边框由它的 hairline 单独包办，这里再挂 .border
          工具类就是 AE 判据点名的 2px 双线边；shadow-paper 一直被家族那条
          !important 压着，从没画出来过，留着只会让人以为改得动投影。 */}
      <div className="desktop-panel-card motion-panel bg-paper-panel p-6">
        <div className="mb-3 flex items-center gap-2">
          <Lightbulb size={18} className="text-copper" />
          <h2 className="paper-title text-xl font-semibold">旧灵感中心已并入全局收件箱</h2>
        </div>
        <p className="text-sm leading-6 text-paper-muted">
          旧灵感数据已统一迁移到「全局收件箱」，作为唯一的想法收集入口。你可以在收件箱里新建想法、编辑、转资料卡、查看来源与 AI 候选，并继续沿用旧的灵感条目与候选版本。
        </p>
        <div className="mt-4 flex flex-wrap gap-2">
          <Button onClick={() => setScreen("inbox")}>
            <InboxIcon size={16} />
            前往全局收件箱
          </Button>
        </div>
      </div>
    </div>
  );
}
