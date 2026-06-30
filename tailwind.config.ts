import type { Config } from "tailwindcss";

export default {
  content: ["./index.html", "./src/**/*.{ts,tsx}"],
  theme: {
    extend: {
      colors: {
        paper: {
          bg: "#f7f0e7",
          panel: "#fffaf2",
          soft: "#f2e4da",
          line: "#d9c9b8",
          ink: "#241b16",
          muted: "#7b6b5e"
        },
        copper: {
          DEFAULT: "#8a5a2b",
          soft: "#b48758",
          dark: "#69411f"
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
