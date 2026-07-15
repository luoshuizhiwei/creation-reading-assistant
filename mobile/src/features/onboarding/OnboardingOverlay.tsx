import { useState } from "react";
import { BookOpen, Sparkles, RefreshCw, ChevronRight, X } from "lucide-react";

const ONBOARDED_KEY = "creation-reading-assistant-mobile-onboarded-v1";

export function hasCompletedOnboarding(): boolean {
  try {
    return localStorage.getItem(ONBOARDED_KEY) === "1";
  } catch {
    return false;
  }
}

function markOnboardingComplete(): void {
  try {
    localStorage.setItem(ONBOARDED_KEY, "1");
  } catch {
    // 忽略
  }
}

interface OnboardingStep {
  icon: typeof BookOpen;
  title: string;
  desc: string;
}

const ONBOARDING_STEPS: OnboardingStep[] = [
  {
    icon: BookOpen,
    title: "导入本地书籍",
    desc: "支持 TXT、Markdown、EPUB 三种格式，自动识别编码，导入后离线阅读。"
  },
  {
    icon: Sparkles,
    title: "边读边记灵感",
    desc: "阅读时选中文字即可转为灵感、高亮或笔记，灵感支持状态流转和批量整理。"
  },
  {
    icon: RefreshCw,
    title: "与电脑端同步",
    desc: "在「我的」页面连接电脑端，可以同步书籍、进度、灵感和笔记，WebDAV 也支持。"
  }
];

export function OnboardingOverlay({ onClose }: { onClose: () => void }) {
  const [step, setStep] = useState(0);
  const isLast = step >= ONBOARDING_STEPS.length - 1;
  const current = ONBOARDING_STEPS[step];
  const Icon = current.icon;

  const handleFinish = () => {
    markOnboardingComplete();
    onClose();
  };

  const handleSkip = () => {
    markOnboardingComplete();
    onClose();
  };

  return (
    <div className="onboarding-overlay">
      <div className="onboarding-panel">
        <button className="onboarding-skip" onClick={handleSkip} aria-label="跳过引导">
          <X size={18} />
          跳过
        </button>
        <div className="onboarding-visual">
          <span className="onboarding-icon">
            <Icon size={36} strokeWidth={1.6} />
          </span>
          <div className="onboarding-dots">
            {ONBOARDING_STEPS.map((_, index) => (
              <span key={index} className={index === step ? "active" : ""} />
            ))}
          </div>
        </div>
        <div className="onboarding-content">
          <h2>{current.title}</h2>
          <p>{current.desc}</p>
        </div>
        <div className="onboarding-actions">
          {!isLast ? (
            <button className="onboarding-next" onClick={() => setStep((s) => s + 1)}>
              下一步
              <ChevronRight size={18} />
            </button>
          ) : (
            <button className="onboarding-finish" onClick={handleFinish}>
              开始使用
            </button>
          )}
        </div>
      </div>
    </div>
  );
}
