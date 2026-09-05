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
        sans: ["Inter", "'Noto Sans SC'", "'Microsoft YaHei'", "ui-sans-serif", "system-ui", "sans-serif"],
        serif: ["'Noto Serif SC'", "'SimSun'", "ui-serif", "serif"],
        mono: ["'JetBrains Mono'", "ui-monospace", "monospace"]
      },
      boxShadow: {
        paper: "0 18px 50px rgba(34, 38, 48, 0.1)",
        lift: "0 6px 18px rgba(34, 38, 48, 0.07)"
      }
    }
  },
  plugins: []
} satisfies Config;
