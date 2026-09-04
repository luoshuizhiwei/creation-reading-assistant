import { resolve } from "node:path";
import { defineConfig, externalizeDepsPlugin } from "electron-vite";
import react from "@vitejs/plugin-react";

export default defineConfig({
  main: {
    plugins: [externalizeDepsPlugin()],
    build: {
      rollupOptions: {
        input: resolve(__dirname, "electron/main/index.ts")
      }
    }
  },
  preload: {
    plugins: [externalizeDepsPlugin()],
    build: {
      rollupOptions: {
        input: resolve(__dirname, "electron/preload/index.ts")
      }
    }
  },
  renderer: {
    root: ".",
    resolve: {
      alias: {
        "@": resolve(__dirname, "src")
      }
    },
    plugins: [react()],
    build: {
      rollupOptions: {
        input: "index.html",
        output: {
          manualChunks(id) {
            if (id.includes("node_modules")) {
              if (id.includes("@tiptap") || id.includes("prosemirror")) {
                return "vendor-tiptap";
              }
              if (id.includes("epubjs")) {
                return "vendor-epubjs";
              }
              if (id.includes("react") || id.includes("react-dom") || id.includes("scheduler")) {
                return "vendor-react";
              }
              if (id.includes("zustand")) {
                return "vendor-zustand";
              }
            }
          }
        }
      }
    }
  }
});
