# 创作阅读助手手机版与电脑端数据互通实施记录

日期：2026-06-30

## 已完成

1. 同步数据层
   - 新增 `src/types/sync.ts`。
   - `InspirationItem`、`LibraryBook`、`ReadingProgress`、`ReadingSession` 增加 `revision/deviceId/deletedAt?`。
   - Electron main 读取旧 JSON 时自动 normalize 同步字段。

2. 电脑端同步服务
   - 新增局域网 HTTP 同步服务。
   - 实现 manifest、pull、push、pair、书籍文件下载、分块下载。
   - 设置页新增“手机同步”卡片，可开启服务、生成配对信息、查看/断开设备。

3. 同步冲突处理
   - `mergeByUpdatedAt` 处理普通字段最新胜出。
   - `mergeIncomingInspirations` 在灵感正文冲突时生成“冲突副本”。
   - 阅读 session 按 id 合并，阅读进度按 bookId 合并。

4. 手机端骨架
   - 新增 `mobile/` Capacitor + Vite + React 工程。
   - 新增离线 localStorage 存储适配。
   - 新增局域网同步客户端。
   - 新增灵感中心、本地书库、扫码连接电脑三个入口。
   - 已执行 `npx cap add android` 与 `npx cap sync android`，生成 Android 平台目录并同步 Web assets。

5. 验证脚本
   - 新增 `verify:sync-schema`
   - 新增 `verify:sync-server`
   - 新增 `verify:mobile-adapter`
   - 新增 `verify:sync-conflicts`
   - 并纳入 `verify:beta`。

## 已验证

- `npm run verify:beta`
- `npm run mobile:build`
- `npm audit --prefix mobile --omit=dev`
- `npx cap sync android`
- `mobile/android/gradlew.bat assembleDebug`

## APK 产物

- Debug APK：`mobile-release/creation-reading-assistant-mobile-debug.apk`
- SHA256：`AC0A83C462E30038B5ADC3E403D493F7F86517B1D803B4BF8DE353DBF4F59A39`

## 后续待做

- 手机端接入真实扫码能力。
- 手机端用 Capacitor Filesystem 保存书籍文件，替换 localStorage 快照式 MVP。
- 手机端实现 TXT/Markdown/EPUB 阅读器。
- 手机端实现阅读页“记为灵感”和结构化来源卡片。
- 在真实 Android 设备上验收安装、配对和同步。
- 同步接口增加更严格的配对设备鉴权和传输加密方案评估。
