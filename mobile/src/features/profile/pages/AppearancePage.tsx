import { CheckCircle2, Moon } from "lucide-react";
import type { MobileAppTheme } from "../../../utils/mobile-helpers";
import { APP_THEMES } from "../profile-constants";
import type { ProfileSubPage } from "../ProfilePage";

export function AppearancePage({
  appTheme,
  onAppThemeChange,
  onSetActivePage
}: {
  appTheme: MobileAppTheme;
  onAppThemeChange: (theme: MobileAppTheme) => void;
  onSetActivePage: (page: ProfileSubPage | undefined) => void;
}) {
  return (
    <div className="screen-stack profile-subpage">
      <header className="mobile-header row-header subpage-header">
        <button className="ghost-button back-button" onClick={() => onSetActivePage(undefined)}>
          ← 返回
        </button>
        <div>
          <p className="mini-label">外观</p>
          <h1>应用外观</h1>
        </div>
      </header>
      <section className="subpage-card">
        <p className="subtle">应用外观影响首页、书架、灵感、统计和设置；阅读页正文背景仍在阅读器设置里单独控制。</p>
        <div className="option-list">
          {APP_THEMES.map(([value, title, desc]) => (
            <button key={value} className={appTheme === value ? "option-row active" : "option-row"} onClick={() => onAppThemeChange(value)}>
              <span><Moon size={18} /></span>
              <div><strong>{title}</strong><small>{desc}</small></div>
              {appTheme === value && <CheckCircle2 size={18} />}
            </button>
          ))}
        </div>
      </section>
    </div>
  );
}
