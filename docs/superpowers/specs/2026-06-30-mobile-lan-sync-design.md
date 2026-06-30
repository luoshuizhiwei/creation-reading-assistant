# 创作阅读助手手机版与局域网同步设计

日期：2026-06-30

## 定位

第一版手机版采用 Android + Capacitor。电脑端继续作为本地优先主应用，手机端作为随身阅读和灵感记录入口。两端通过同一局域网直连同步，不引入云账号服务器。

## 范围

- 手机端主入口：灵感中心、本地书库、扫码连接电脑。
- 电脑端新增“手机同步”设置区：开启/关闭同步服务、生成一次性配对码、查看和断开已配对设备。
- 同步范围：灵感、AI 候选版本、书库元数据、阅读进度、阅读 session、书籍文件清单与下载。
- 明确不同步：AI API Key、`ai-secrets.json`、日志、调试导出、备份包。

## 数据模型

所有参与同步的实体补齐：

- `revision`
- `deviceId`
- `deletedAt?`

旧 JSON 数据在读取时自动 normalize，缺少同步字段时按本机 `deviceId` 和 `revision = 1` 补齐。

## 同步协议

电脑端 Electron main process 启动本地 HTTP 服务，只监听局域网地址。

- `GET /sync/manifest`
- `POST /sync/pull`
- `POST /sync/push`
- `POST /sync/pair`
- `GET /sync/books/:bookId/file`
- `GET /sync/books/:bookId/chunks/:index`

二维码载荷包含 host、port、一次性 token 和 pairing URL。token 默认 10 分钟有效。

## 冲突策略

- 普通字段：按 `updatedAt` 最新胜出；时间相同按 `revision` 兜底。
- 灵感正文双端修改：保留胜出版本，同时为输掉版本生成“冲突副本”，避免静默丢内容。
- 阅读 session：按 session id 去重。
- 阅读时长：继续沿用 session 汇总，避免重复累加。
- 删除：保留 `deletedAt` tombstone，避免旧设备把已删除数据复活。

## 安全边界

- AI Key 不进入 sync manifest/pull/push。
- 同步服务由用户在设置页手动开启。
- 配对 token 一次性使用，过期后需要重新生成。
- 第一版仍是局域网信任模型；公网穿透、云同步、账号体系不在本阶段范围。

## 手机端第一版限制

当前 mobile 工程是可编译的 Android/Capacitor 骨架，并已通过 `npx cap add android` 生成 Android 平台目录，已具备：

- 离线 localStorage 快照；
- 配对信息解析；
- manifest/pull/push 客户端；
- 书籍文件和分块下载客户端；
- 灵感/书库/同步三入口 UI。

后续还需要继续补齐手机端完整阅读器、相机扫码、Android 原生文件保存、EPUB/TXT/Markdown 渲染和正式签名发布。2026-06-30 已生成 debug APK：`mobile-release/creation-reading-assistant-mobile-debug.apk`，用于第一轮真机安装和局域网同步 smoke。
