import type { Config } from "tailwindcss";

export default {
  content: ["./index.html", "./src/**/*.{ts,tsx}"],
  theme: {
    extend: {
      colors: {
        // 全部走 styles.css 的主题变量：换肤只改 token，不改这里。
        paper: {
          bg: "rgb(var(--rgb-app) / <alpha-value>)",
          panel: "rgb(var(--rgb-surface) / <alpha-value>)",
          soft: "rgb(var(--rgb-subtle) / <alpha-value>)",
          line: "rgb(var(--rgb-line) / <alpha-value>)",
          ink: "rgb(var(--rgb-ink) / <alpha-value>)",
          muted: "rgb(var(--rgb-muted) / <alpha-value>)"
        },
        copper: {
          DEFAULT: "rgb(var(--rgb-accent) / <alpha-value>)",
          soft: "var(--copper-soft)",
          dark: "var(--copper-dark)"
        },
        moss: {
          DEFAULT: "rgb(var(--rgb-moss) / <alpha-value>)",
          soft: "var(--moss-soft)"
        }
      },
      fontFamily: {
        // 指向 styles/tokens.css 的权威令牌，避免这里另写一份字体栈。
        // 原先硬写 "Inter"：包内不分发该字体（仓库无任何 woff/ttf，
        // index.html CSP 的 font-src 只允许 'self' data: file:），
        // Windows 上拿不到 Inter，实际静默回退——与 --font-ui 的栈不一致。
        sans: ["var(--font-ui)"],
        serif: ["var(--font-content)"],
        mono: ["var(--font-data)"]
      },
      boxShadow: {
        paper: "0 18px 50px rgba(34, 38, 48, 0.1)",
        lift: "0 6px 18px rgba(34, 38, 48, 0.07)"
      }
    }
  },
  plugins: []
} satisfies Config;
