import type { Config } from "tailwindcss";

export default {
  content: ["./index.html", "./src/**/*.{ts,tsx}"],
  theme: {
    extend: {
      colors: {
        paper: {
          bg: "#f2efe7",
          panel: "#fbf8f1",
          soft: "#e9e2d4",
          line: "#cfc5b2",
          ink: "#1f2421",
          muted: "#6d756f"
        },
        copper: {
          DEFAULT: "#365c4a",
          soft: "#8fa79d",
          dark: "#27453a"
        },
        moss: {
          DEFAULT: "#506354",
          soft: "#dce9dc"
        }
      },
      fontFamily: {
        sans: ["\"Noto Sans SC\"", "\"Microsoft YaHei\"", "ui-sans-serif", "system-ui", "sans-serif"],
        serif: ["\"Noto Serif SC\"", "\"SimSun\"", "ui-serif", "serif"],
        mono: ["\"JetBrains Mono\"", "ui-monospace", "monospace"]
      },
      boxShadow: {
        paper: "0 18px 50px rgba(57, 39, 25, 0.08)",
        lift: "0 8px 24px rgba(57, 39, 25, 0.06)"
      }
    }
  },
  plugins: []
} satisfies Config;
