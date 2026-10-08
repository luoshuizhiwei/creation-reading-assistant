import { forwardRef, type ButtonHTMLAttributes, type InputHTMLAttributes, type ReactNode, type TextareaHTMLAttributes } from "react";
import { Loader2 } from "lucide-react";
import "@/features/settings/settings-controls.css";

/**
 * 按钮强调层级（规格 §2）
 * primary      单一主操作，每个视图最多一个
 * tonal        次级但常用的操作（比 outline 更醒目，不着色块边）
 * outline      默认次要操作，等价于旧 secondary
 * ghost        弱化操作，等价于旧 quiet
 * icon         纯图标，必须给中文 aria-label
 * danger-outline 可撤销的风险操作（移出、归档）
 * danger-filled  不可撤销的破坏性操作（删除），文案必须说出结果
 */
export type ButtonVariant =
  | "primary"
  | "tonal"
  | "outline"
  | "ghost"
  | "icon"
  | "danger-outline"
  | "danger-filled";

/** 迁移期别名：旧调用继续可用，内部归一到新名（规格 §2 落地第 4 步） */
export type ButtonVariantInput = ButtonVariant | "secondary" | "quiet";

export type ButtonSize = "sm" | "md" | "lg";

const VARIANT_ALIAS: Record<string, ButtonVariant> = {
  secondary: "outline",
  quiet: "ghost"
};

export const Button = forwardRef<
  HTMLButtonElement,
  ButtonHTMLAttributes<HTMLButtonElement> & {
    variant?: ButtonVariantInput;
    size?: ButtonSize;
    /** 进行中：置灰、aria-busy、转圈，且保持宽度不跳（规格 §2.6） */
    loading?: boolean;
  }
>(function Button({ children, className = "", variant = "primary", size = "md", loading = false, disabled, type, ...props }, ref) {
  const resolved = (VARIANT_ALIAS[variant] ?? variant) as ButtonVariant;

  // 规格 §2.5：按钮一律无阴影；§2.4：反馈只靠颜色，不做位移与缩放。
  // 焦点环走 --focus-ring。注意不能写 shadow-[var(--focus-ring)]，
  // Tailwind 会把它当成阴影颜色、只产出 --tw-shadow-color 而没有 box-shadow 声明，
  // 因此用任意属性形式。
  //
  // base 里绝不能放内边距：Tailwind 同类工具按「值从小到大」发射，
  // px-3 排在 px-2 / p-0 之后，写进 base 就会压掉尺寸层与 icon 的 p-0
  // （sm 实际拿到 12px 内边距，icon 按钮在 28×28 方框里只剩 4px 内容区）。
  // 同一属性只允许一层拥有：内边距归 sizes，icon 由 variant 的 p-0 负责。
  //
  // 批次 AV：圆角走令牌形式 rounded-[var(--radius-1)]，不再是那个刻度上没有的 8px 档。
  // 规格 §5.2 把控件档定在 --radius-1=4px，样张 .btn 也是它；仓内那批
  // 遗留「按钮容器」接管规则（.desktop-page-actions button / .settings-nav button →
  // var(--radius-control)，.project-nav / .uni-search-filters button → var(--radius-1)）
  // 本来就是 4px——组件留在 8px 等于「同一控件两个圆角」，谁被接管规则盖到谁变，
  // 这正是第 8 步要收敛的形状，不是要保留的特征。
  // ⚠ 写法选任意值形式（引用令牌本身）而不是同值的裸 4px 档，两条实测出来的理由：
  //   1) 圆角这一族的发射顺序既不是字母序、也不是上面内边距那条「值从小到大」的规律
  //      （那是另一族工具的实测，别套用），产物实测（后者压前者）：裸档 → 16px 档 →
  //      任意值档 → full → 8px → 6px → none → 2px → 12px。旧写法用的 8px 档排在 full
  //      与任意值档之后，会把消费方挂的圆角类静默压掉（jsdom 实测「base + 全圆角类」
  //      算出 0.5rem，不是 9999px）；任意值档发在 full/8px/6px/none/2px/12px 之前，
  //      base 只提供默认值，不与「同一属性只允许一层拥有」打架（只有裸档和 16px 档发在
  //      它之前、覆盖不掉，实测今天无人这么写）。
  //   2) 裸档是 0.25rem，值随根字号缩放；令牌形式跟着 --radius-1 走。
  //   ⚠ 任何 src/ 文件的注释里都不要写这类工具类的字面串：Tailwind 的 content 扫描
  //   不剥注释，写了就把没人用的工具类发射进产物（批次 AV 实测一次，胜者表当场多出新键）。
  //   （base 的圆角今天没有任何消费方覆盖：236 个 Button/RingButton 开标签里 0 个挂圆角类，
  //   所以第 1 条是「别把陷阱留在原地」，不是「正在修一个坏掉的界面」。）
  const base =
    "inline-flex items-center justify-center gap-2 rounded-[var(--radius-1)] text-sm font-medium transition-colors duration-150 disabled:cursor-not-allowed disabled:opacity-55 focus-visible:outline-none focus-visible:[box-shadow:var(--focus-ring)]";

  // md 暂留 36px：工具栏里按钮与 .paper-input（同为 h-9）并排，
  // 默认高度必须与输入框同一批收敛到 32px，否则错位（排在规格 §6 第 8 步）。
  // 内边距按规格 §2.2：sm 10px / md 12px / lg 16px。
  const sizes: Record<ButtonSize, string> = {
    sm: "h-7 px-2.5 text-xs",
    md: "h-9 px-3",
    lg: "h-10 px-4"
  };

  const variants: Record<ButtonVariant, string> = {
    primary: "bg-copper text-[color:var(--fg-on-solid)] hover:bg-copper-dark",
    // 不能用 bg-copper-soft：别名层把 --copper-soft 映射到 --proof-tint（校样红的底），
    // 配上 text-copper（印刷蓝）就是红底蓝字——和 D-1 同族的遗留别名陷阱。
    // 主色底必须显式写 --action-tint。
    tonal:
      "bg-[color:var(--action-tint)] text-copper hover:bg-[color-mix(in_srgb,var(--action-primary)_14%,var(--action-tint))]",
    outline: "border border-paper-line bg-paper-panel text-paper-ink hover:border-copper/50 hover:bg-paper-soft/60",
    ghost: "text-paper-muted hover:bg-paper-soft/70 hover:text-paper-ink",
    icon: "p-0 text-paper-muted hover:bg-paper-soft/70 hover:text-paper-ink",
    // 注意：不能写 border-[color:var(--proof-mark)]/45。Tailwind 无法给 var() 套
    // alpha 修饰符，整条声明会被静默丢弃（按钮变成无边框色）。改用 color-mix。
    "danger-outline":
      "border border-[color:color-mix(in_srgb,var(--proof-mark)_45%,transparent)] text-[color:var(--proof-mark)] hover:bg-[color:var(--proof-tint)]",
    "danger-filled": "bg-[color:var(--proof-mark)] text-[color:var(--fg-on-solid)] hover:bg-[color-mix(in_srgb,var(--proof-mark)_88%,#000)]"
  };

  // 纯图标按钮不需要左右内边距，用正方形尺寸
  const sizeClass = resolved === "icon" ? (size === "sm" ? "h-7 w-7" : size === "lg" ? "h-10 w-10" : "h-9 w-9") : sizes[size];

  return (
    <button
      ref={ref}
      type={type ?? "button"}
      data-variant={variant}
      data-resolved-variant={resolved}
      data-size={size}
      aria-busy={loading || undefined}
      disabled={disabled || loading}
      className={`${base} ${sizeClass} ${variants[resolved]} ${loading ? "relative" : ""} ${className}`}
      {...props}
    >
      {loading ? (
        <>
          {/* 宽度稳定：不可见的原文字撑住尺寸，转圈叠在中间 */}
          <span className="invisible inline-flex items-center gap-1.5">{children}</span>
          <Loader2 size={size === "sm" ? 12 : 14} className="absolute animate-spin" aria-hidden />
        </>
      ) : (
        children
      )}
    </button>
  );
});

export function TextInput({ className = "", ...props }: InputHTMLAttributes<HTMLInputElement>) {
  return (
    <input
      className={`paper-input h-9 ${className}`}
      {...props}
    />
  );
}

export function TextArea({ className = "", ...props }: TextareaHTMLAttributes<HTMLTextAreaElement>) {
  return (
    <textarea
      className={`paper-input px-3 py-2 leading-6 ${className}`}
      {...props}
    />
  );
}

export function Field({ label, children }: { label: string; children: ReactNode }) {
  return (
    <label className="grid gap-1.5 text-sm text-paper-muted">
      <span className="font-medium text-paper-ink">{label}</span>
      {children}
    </label>
  );
}

export function ShellPanel({ children, className = "" }: { children: ReactNode; className?: string }) {
  return <section className={`paper-panel ${className}`}>{children}</section>;
}

export function EmptyState({ title, body }: { title: string; body: string }) {
  return (
    <div className="grid h-full place-items-center px-8 py-10 text-center" role="status" aria-live="polite">
      <div className="max-w-sm">
        <h2 className="paper-title text-lg font-semibold">{title}</h2>
        <p className="mt-2 text-sm leading-6 text-paper-muted">{body}</p>
      </div>
    </div>
  );
}

export * from "./ui/Dialog";
export * from "./ui/Tabs";
export * from "./ui/Select";
export * from "./ui/Spinner";
export * from "./ui/Slider";
export * from "./ui/Switch";
export * from "./ui/NumberStepper";
export * from "./ui/FontPicker";
