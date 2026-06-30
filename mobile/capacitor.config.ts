import type { CapacitorConfig } from "@capacitor/cli";

const config: CapacitorConfig = {
  appId: "local.creationReadingAssistant.mobile",
  appName: "创作阅读助手",
  webDir: "dist",
  android: {
    allowMixedContent: true
  }
};

export default config;
