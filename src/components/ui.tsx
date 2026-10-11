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
  // base 里绝不能放内边距：Tailwind 同类工具按**类名字符串字典序**发射（批次 BD-21 纠正：
  // 旧说法「按值从小到大」是巧合——px-2.5 / px-3 恰好值序=字序；同族露馅的是 px-10 排在
  // px-2 之前）。px-3 排在 px-2.5 / p-0 之后，写进 base 就会压掉尺寸层与 icon 的 p-0
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
  //   1) 产物实测的圆角族发射序（后者压前者）：裸档 → 16px 档 → 任意值档 → full → 8px →
  //      6px → none → 2px → 12px。旧写法用的 8px 档排在 full
  //      与任意值档之后，会把消费方挂的圆角类静默压掉（jsdom 实测「base + 全圆角类」
  //      算出 0.5rem，不是 9999px）；任意值档发在 full/8px/6px/none/2px/12px 之前，
  //      base 只提供默认值，不与「同一属性只允许一层拥有」打架（只有裸档和 16px 档发在
  //      它之前、覆盖不掉，实测今天无人这么写）。
  //      ✔ 批次 BD-21 复核：AV 当时把它写成「既不是字母序、也不是值从小到大」，前半句是
  //      误判——AV 列的那串（裸档 → 2xl → 任意值 → full → 8px(md) → 6px → none → 2px(sm) →
  //      12px(xl)）本来就是字母序，只是当时没认出来（圆角名与 px 值的对应见守卫的发射序列表）。
  //      BD-21 之后仓里 lg/xl/sm/none 全无真实消费方，产物里只剩 裸档 → 三个任意值档 →
  //      full → md 六个键，逐字重扫仍是字典序。结论不变，
  //      判序的规律从此只有一条：按类名字符串排。
  //   2) 裸档是 0.25rem，值随根字号缩放；令牌形式跟着 --radius-1 走。
  //   ⚠ 任何 src/ 文件的注释里都不要写这类工具类的字面串：Tailwind 的 content 扫描
  //   不剥注释，写了就把没人用的工具类发射进产物（批次 AV 实测一次，胜者表当场多出新键）。
  //   （base 的圆角今天没有任何消费方覆盖：236 个 Button/RingButton 开标签里 0 个挂圆角类，
  //   所以第 1 条是「别把陷阱留在原地」，不是「正在修一个坏掉的界面」。）
  // 批次 BD-21（§2.2 尺寸批）：base 里的「十四px 具名档」**删掉了**，字号交回尺寸层。
  // 这不是整理，是这批唯一能让 md=13px 真正生效的写法，理由要写死在这里：
  // Tailwind 同类工具的真实发射顺序是**按类名字符串排序**，不是按值大小——产物实测的
  // 字序见守卫 verify-design-tokens.mjs「发射顺序」注（那边不在 Tailwind 扫描范围内，
  // 可以照抄类名串；这里不能写，写了就是幻影工具类）。要点只有一个：任意值档
  // （方括号那族）发在 md 要的 13 之前，具名「十四」档发在它之后，两边同挂时是 14 赢——
  // 照直觉写「尺寸层覆盖 base」会得到一个静默失效的 13px，界面纹丝不动、
  // 账面上却像已经收敛了。同一属性的所有权必须只有一层：字号归 sizes，base 不再参与。
  // ⚠ 顺带纠正本文件与守卫里那句旧说法「按值从小到大发射」：结论（base 的内边距
  //   会压掉尺寸层）仍然成立，但成立的真正原因是这三档的内边距恰好值序=字序；
  //   换成「任意值 vs 具名档」就反过来了。守卫里已同步改。
  const base =
    "inline-flex items-center justify-center gap-2 rounded-[var(--radius-1)] font-medium transition-colors duration-150 disabled:cursor-not-allowed disabled:opacity-55 focus-visible:outline-none focus-visible:[box-shadow:var(--focus-ring)]";

  // 批次 BD-21：规格 §2.2 三档 28 / 32 / 40 落地，md 从 36px 收到 32px（默认档，
  // 实测 182 个不写 size 的 <Button> 一起变矮 4px（RingButton 不吃 sizes，不在其内），
  // 这是可感知改版、不是零视觉）。
  // 上面那句「md 暂留 36px：与 .paper-input（同为 36 高）并排」的许诺由本批兑现：
  // 配对输入框的 36 高也同批改 32，共 18 处（TextInput / Select / FontPicker 三个原语
  // 内部各 1，消费方 paper-input 15 处），另加 LibraryPage 里与图标档并排的裸按钮 1 处
  // （它不是输入框，但同排的 <Button> 图标档 36→32，不改就差 4px）。
  // 两处 CSS 侧的 36 高同批收敛：settings-controls.css 的 .paper-stepper（与 FontPicker
  // 同列同排）与 styles.css 的 .writing-project-switcher select（宿主就是 Select 组件）；
  // editorial-studio.css @media(max-width:1024px) 里 .project-nav button 的
  // min-height: 36px **不动**——那条规则基态是 38px，属遗留接管家族，单独收窄屏档会
  // 自相矛盾，整族随「写作页 LEGACY 迁移批」判。
  // 字号按 §2.2：sm 12 / md 13 / lg 14（原先三档全吃 base 的 14，sm 例外自带 12）。
  // 内边距按规格 §2.2：sm 10px / md 12px / lg 16px。
  const sizes: Record<ButtonSize, string> = {
    sm: "h-7 px-2.5 text-xs",
    md: "h-8 px-3 text-[13px]",
    lg: "h-10 px-4 text-sm"
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
  // 批次 BD-21：md 档跟着 36→32（方框边长同步收一档），否则同一工具栏里
  // 文字按钮 32、图标按钮 36，比改之前更花。
  // ⚠ 这一支必须**自带**字号：base 的十四px 档已交回尺寸层（见上面 BD-21 的发射顺序），
  //   不写就会从父级继承（正文 14 / 其它档不一），等于把 icon 档的字号交给运气。
  //   三档与 sizes 同名同值，改前 icon 三档吃的是 base 14 / 十二 / base 14，
  //   现在 sm 12 不变、md 14→13（与 md 档同批）、lg 14 不变。
  const sizeClass =
    resolved === "icon"
      ? size === "sm"
        ? "h-7 w-7 text-xs"
        : size === "lg"
          ? "h-10 w-10 text-sm"
          : "h-8 w-8 text-[13px]"
      : sizes[size];

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
      className={`paper-input h-8 ${className}`}
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
        <h2 className="paper-title text-base font-semibold">{title}</h2>
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
