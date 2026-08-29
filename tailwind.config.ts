import type { Config } from "tailwindcss";

export default {
  content: ["./index.html", "./src/**/*.{ts,tsx}"],
  // 阅读器主题类名在运行时拼接（reader-shell-${background}），内容扫描看不到字面量；
  // 不 safelist 会被 @layer components 整块清除，阅读主题背景在生产构建中丢失。
  safelist: [{ pattern: /^reader-(bg|shell)-(white|warm|green|night|amber|parchment|beans)$/ }],
  theme: {
    extend: {
      colors: {
        paper: {
          bg: "#f1f0eb",
          panel: "#ffffff",
          soft: "#f6f5f1",
          line: "#e4e2db",
          ink: "#252830",
          muted: "#646b77"
        },
        copper: {
          DEFAULT: "#33538f",
          soft: "#7e9cd6",
          dark: "#27436f"
        },
        moss: {
          DEFAULT: "#3e7a5e",
          soft: "#e4f0e9"
        }
      },
      fontFamily: {
        sans: ["Inter", "\"Noto Sans SC\"", "\"Microsoft YaHei\"", "ui-sans-serif", "system-ui", "sans-serif"],
        serif: ["\"Noto Serif SC\"", "\"SimSun\"", "ui-serif", "serif"],
        mono: ["\"JetBrains Mono\"", "ui-monospace", "monospace"]
      },
      boxShadow: {
        paper: "0 18px 50px rgba(34, 38, 48, 0.1)",
        lift: "0 6px 18px rgba(34, 38, 48, 0.07)"
      }
    }
  },
  plugins: []
} satisfies Config;
