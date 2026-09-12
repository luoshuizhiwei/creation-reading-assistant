/**
 * Stage 4-D 真实 Electron 验收：场景 AI 的续写 / 精简 / 角色一致性。
 *
 * 运行构建产物（`out/main/index.js` + 真实 preload + 真实渲染包）在隔离 profile 中启动，
 * AI 服务端用本机 mock HTTP 服务顶替，覆盖单元测试与契约测试都碰不到的部分：
 * 1. 提示词是否真的带着场景标题/正文/上下文发到了外部服务（而不是只在渲染层拼对）；
 * 2. AI 返回后正文是否**仍然没有被改写**——候选与报告都必须停在确认之前；
 * 3. 续写采纳是否按「追加」语义落库（原正文保留 + 新段落），而不是整篇替换；
 * 4. 角色一致性是否只有只读报告、没有采纳入口；
 * 5. 发送确认里被排除的上下文组，是否真的没有出现在发出的提示词里。
 *
 * 未覆盖（如实记录，不冒充）：
 * - 真实第三方大模型（网络不可用），由 mock 服务端按 OpenAI 兼容协议返回固定内容；
 * - electron-builder 打包态（asar）由阶段 4 收口的打包验收覆盖，不在本脚本内；
 * - 生成质量/可用性评估：本脚本只验证链路与边界，不评价 AI 输出文本。
 */
import { existsSync, mkdirSync, mkdtempSync } from "node:fs";
import os from "node:os";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { createServer } from "node:http";
import { _electron as electron } from "playwright-core";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(__dirname, "..");
const electronPath = path.join(root, "node_modules", "electron", "dist", "electron.exe");
const mainJs = path.join(root, "out", "main", "index.js");
const evidenceDirectory = path.join(root, "output", "playwright", "stage4-scene-ai");
const temporaryRoot = mkdtempSync(path.join(os.tmpdir(), "creation-stage4-ai-"));
const profileDir = path.join(temporaryRoot, "profile");

mkdirSync(profileDir, { recursive: true });
mkdirSync(evidenceDirectory, { recursive: true });

process.on("unhandledRejection", (error) => {
  console.error("[stage4-scene-ai][unhandledRejection]", error);
});
process.on("uncaughtException", (error) => {
  console.error("[stage4-scene-ai][uncaughtException]", error);
});

const checks = [];
function check(name, ok, detail = "") {
  checks.push({ name, ok, detail });
  console.log(`[stage4-scene-ai] ${ok ? "PASS" : "FAIL"} ${name}${detail ? ` — ${detail}` : ""}`);
  if (!ok) throw new Error(`${name}: ${detail || "acceptance failed"}`);
}

async function settlePaint(page, delay = 420) {
  await page.evaluate(() => new Promise((resolve) => requestAnimationFrame(() => requestAnimationFrame(() => resolve()))));
  await page.waitForTimeout(delay);
}

const ORIGINAL_BODY = "洛水之蔚站在河边。\n慕辰从桥上走下来。";
const CONTINUATION = "两人都没先开口。河水把灯影揉成一片碎金。";
const CONDENSED = "洛水之蔚立在河边，慕辰自桥上下来。";
const CHARACTER_REPORT = "【洛水之蔚 | 一致 | 全程克制，未见突兀情绪】\n【慕辰 | 偏离 | 此处直接发问与设定的寡言冲突 → 改为沉默】\n【总体结论】一人一致，一人偏离。";

/** OpenAI 兼容协议的 mock 服务端：记录收到的提示词，按队列返回内容。 */
function startMockAiServer() {
  const prompts = [];
  let queue = [];
  const server = createServer((req, res) => {
    let raw = "";
    req.on("data", (chunk) => {
      raw += chunk;
    });
    req.on("end", () => {
      let prompt = "";
      try {
        const parsed = JSON.parse(raw);
        const messages = Array.isArray(parsed?.messages) ? parsed.messages : [];
        prompt = messages.map((message) => String(message?.content ?? "")).join("\n");
      } catch {
        prompt = raw;
      }
      prompts.push(prompt);
      const content = queue.length > 0 ? queue.shift() : "（mock 默认回复）";
      res.writeHead(200, { "Content-Type": "application/json" });
      res.end(
        JSON.stringify({
          id: "chatcmpl-mock",
          object: "chat.completion",
          created: Math.floor(Date.now() / 1000),
          model: "acceptance-model",
          choices: [{ index: 0, message: { role: "assistant", content }, finish_reason: "stop" }]
        })
      );
    });
  });
  return {
    prompts,
    queueReply: (text) => queue.push(text),
    listen: () =>
      new Promise((resolve) => {
        server.listen(0, "127.0.0.1", () => resolve(server.address().port));
      }),
    close: () => new Promise((resolve) => server.close(() => resolve()))
  };
}

async function main() {
  check("Electron 与主进程产物存在", existsSync(electronPath) && existsSync(mainJs), mainJs);

  const mock = startMockAiServer();
  const port = await mock.listen();
  check("mock AI 服务端已启动", port > 0, `http://127.0.0.1:${port}/v1`);

  const app = await electron.launch({
    executablePath: electronPath,
    // --in-process-gpu：本机装有虚拟显示适配器，Chromium 独立 GPU 进程会 FATAL 退出。
    args: ["--in-process-gpu", mainJs],
    cwd: root,
    env: { ...process.env, CREATION_READER_CAPTURE_PROFILE: profileDir }
  });

  try {
    const page = await app.firstWindow();
    await page.waitForLoadState("domcontentloaded");
    await page.waitForSelector(".desktop-canvas", { timeout: 30_000 });

    const runtime = await app.evaluate(({ app: electronApp }) => ({
      isPackaged: electronApp.isPackaged,
      userData: electronApp.getPath("userData")
    }));
    check(
      "开发态真实 Electron 且用户数据根隔离到临时目录",
      runtime.isPackaged === false &&
        path.resolve(runtime.userData).toLowerCase().startsWith(path.resolve(profileDir).toLowerCase()),
      JSON.stringify(runtime)
    );

    // ---- 造数据：项目 + 卡片 + 正文 + 批注（分步执行，任何一步失败都能定位） ----
    const projectId = await page.evaluate(async () => {
      const created = await window.api.creation.createProject({
        title: "Stage4 场景 AI 验收",
        template: "blank",
        weeklyUpdateDays: [],
        chapterWorkflow: ["规划", "待写", "写作中", "初稿", "修订", "定稿", "已发布"]
      });
      return created.project.id;
    });
    check("已创建隔离项目", typeof projectId === "string" && projectId.startsWith("project-"), String(projectId));

    const cardId = await page.evaluate(async (id) => {
      const result = await window.api.creation.runStructure({
        type: "card.create",
        projectId: id,
        kind: "character",
        title: "洛水之蔚",
        aliases: ["洛蔚"]
      });
      return result?.entityId ?? "";
    }, projectId);
    check("已创建角色卡（提供关联卡片上下文）", cardId.startsWith("card-"), cardId);

    const sceneId = await page.evaluate(
      async ([id, bodyText]) => {
        const navigation = await window.api.creation.readProjectNavigation(id);
        const scene = navigation.chapters[0].scenes[0];
        const body = await window.api.creation.readSceneBody(scene.id);
        const saved = await window.api.creation.updateSceneBody({
          sceneId: scene.id,
          baseRevision: body?.revision ?? scene.revision,
          body: {
            type: "doc",
            content: bodyText.split("\n").map((text) => ({ type: "paragraph", content: [{ type: "text", text }] }))
          }
        });
        if (!saved.ok) throw new Error(saved.error.message);
        return scene.id;
      },
      [projectId, ORIGINAL_BODY]
    );
    check("已写入两段场景正文", typeof sceneId === "string" && sceneId.startsWith("scene-"), sceneId);

    // 任务卡：出场卡片指向刚建的角色卡，目标/冲突/情绪各填一项，
    // 这样上下文包里「任务卡」「关联卡片」两组都有真实内容可被排除。
    const planning = await page.evaluate(
      async ([scene, card]) => {
        const result = await window.api.creation.runStructure({
          type: "scene.updatePlanning",
          sceneId: scene,
          planning: {
            perspectiveCardId: card,
            time: "雨夜",
            locationCardId: null,
            castCardIds: [card],
            goal: "确认对方是否还记得旧约定",
            conflict: "两人都想先开口，又都在等对方先开口",
            outcome: null,
            emotion: "克制",
            targetWords: null
          }
        });
        return result?.commandType ?? "";
      },
      [sceneId, cardId]
    );
    check("已写入场景任务卡（提供任务卡 + 关联卡片上下文）", planning === "scene.updatePlanning", planning);
    const seed = { projectId, sceneId };

    // ---- 配置 AI：指向本机 mock 服务并保存 Key（真实 safeStorage 路径） ----
    const aiState = await page.evaluate(async (baseUrl) => {
      await window.api.ai.updateSettings({ enabled: true, baseUrl, model: "acceptance-model" });
      await window.api.ai.saveApiKey({ apiKey: "acceptance-key" });
      return window.api.ai.getSettings();
    }, `http://127.0.0.1:${port}/v1`);
    check(
      "AI 已在真实设置中启用并配置 Key",
      aiState.enabled === true && aiState.hasApiKey === true && aiState.baseUrl.includes(`127.0.0.1:${port}`),
      JSON.stringify({ enabled: aiState.enabled, hasApiKey: aiState.hasApiKey, baseUrl: aiState.baseUrl })
    );

    await page.reload();
    await page.waitForLoadState("domcontentloaded");
    await page.waitForSelector(".desktop-canvas", { timeout: 30_000 });

    // ---- 进入写作台 ----
    await page.getByRole("button", { name: "项目", exact: true }).first().click();
    await page.waitForSelector(".project-home-list", { timeout: 15_000 });
    await page.getByRole("button", { name: "打开项目：Stage4 场景 AI 验收" }).click();
    await page.waitForSelector(".project-nav", { timeout: 20_000 });
    await page.getByRole("button", { name: "写作" }).first().click();
    await page.waitForSelector("[data-testid='scene-ai-row']", { timeout: 20_000 });
    await settlePaint(page);

    const labels = await page.locator("[data-testid='scene-ai-row'] button").allInnerTexts();
    check(
      "场景雷达提供六个 AI 动作入口",
      ["润色场景", "扩写场景", "续写场景", "精简场景", "一致性检查", "角色一致性"].every((label) =>
        labels.some((text) => text.includes(label))
      ),
      labels.join(" / ")
    );
    await page.screenshot({ path: path.join(evidenceDirectory, "scene-ai-entries.png") });

    const readBody = () => page.evaluate((sceneId) => window.api.creation.readSceneBody(sceneId), seed.sceneId);
    const bodyTextOf = (view) =>
      (view?.body?.content ?? [])
        .map((block) => (block?.content ?? []).map((inline) => inline?.text ?? "").join(""))
        .filter((text) => text !== "")
        .join("\n");

    // ---- 续写：只出候选，正文必须先保持不变 ----
    mock.queueReply(CONTINUATION);
    await page.getByTestId("scene-ai-continuation").click();
    await page.waitForSelector("[data-testid='ai-send-confirm']", { timeout: 15_000 });
    const confirmLabel = await page.locator("[data-testid='ai-send-confirm'] h2").innerText();
    check("发送确认显式标注动作为「续写场景」", confirmLabel.includes("续写场景"), confirmLabel);
    await page.screenshot({ path: path.join(evidenceDirectory, "scene-ai-continuation-confirm.png") });

    await page.getByTestId("ai-send-confirm-go").click();
    await page.waitForSelector("[data-testid='scene-candidate-review']", { timeout: 25_000 });
    await settlePaint(page);

    const sentPrompt = mock.prompts[mock.prompts.length - 1] ?? "";
    check(
      "提示词真的带着场景标题与正文发到服务端",
      sentPrompt.includes("【场景标题】") && sentPrompt.includes("【场景正文】") && sentPrompt.includes("洛水之蔚站在河边。"),
      sentPrompt.slice(0, 120)
    );
    check("续写指令要求只输出新增内容", sentPrompt.includes("续写") && sentPrompt.includes("不要重复原文"), "");
    check(
      "上下文带上了任务卡与关联卡片",
      sentPrompt.includes("【任务卡】") && sentPrompt.includes("【关联卡片】") && sentPrompt.includes("洛水之蔚"),
      ""
    );

    const appendHint = await page.locator("[data-testid='scene-candidate-append-hint']").innerText();
    check("候选明确提示续写是追加而非替换", appendHint.includes("追加到当前正文末尾"), appendHint);

    const beforeAdopt = bodyTextOf(await readBody());
    check("未确认前正文保持原样，AI 输出不落正文", beforeAdopt === ORIGINAL_BODY, beforeAdopt);
    await page.screenshot({ path: path.join(evidenceDirectory, "scene-ai-continuation-candidate.png") });

    // ---- 续写采纳：追加语义落库 ----
    await page.getByTestId("scene-candidate-accept").click();
    await page.waitForFunction(
      (expected) => {
        const node = document.querySelector("[data-testid='scene-candidate-review']");
        return node === null;
      },
      null,
      { timeout: 25_000 }
    );
    await page.waitForTimeout(600);
    // bodyTextOf 用单换行连接段落，因此期望值 = 原正文 + 换行 + 续写段。
    const afterAdopt = bodyTextOf(await readBody());
    check(
      "续写采纳后正文 = 原正文（段落原样保留）+ 新段落",
      afterAdopt === `${ORIGINAL_BODY}\n${CONTINUATION}`,
      JSON.stringify(afterAdopt)
    );
    await page.screenshot({ path: path.join(evidenceDirectory, "scene-ai-continuation-adopted.png") });

    // ---- 角色一致性：只读报告，无采纳入口 ----
    mock.queueReply(CHARACTER_REPORT);
    await page.getByTestId("scene-ai-character-consistency").click();
    await page.waitForSelector("[data-testid='ai-send-confirm']", { timeout: 15_000 });
    await page.getByTestId("ai-send-confirm-go").click();
    await page.waitForSelector("[data-testid='scene-ai-report']", { timeout: 25_000 });
    await settlePaint(page);

    const reportText = await page.locator("[data-testid='scene-ai-report']").innerText();
    check("角色一致性输出为报告且标注动作名", reportText.includes("角色一致性") && reportText.includes("总体结论"), "");
    const acceptButtons = await page.getByTestId("scene-candidate-accept").count();
    check("报告不提供任何采纳入口", acceptButtons === 0, `accept buttons=${acceptButtons}`);
    const reportPrompt = mock.prompts[mock.prompts.length - 1] ?? "";
    check(
      "角色一致性指令要求只输出报告、不改正文",
      reportPrompt.includes("角色人格/动机一致性检查") && reportPrompt.includes("只输出报告，不改正文"),
      ""
    );
    const afterReport = bodyTextOf(await readBody());
    check("报告生成后正文未被改写", afterReport === afterAdopt, JSON.stringify(afterReport));
    await page.screenshot({ path: path.join(evidenceDirectory, "scene-ai-character-report.png") });
    await page.getByTestId("scene-ai-report-close").click();
    await page.waitForTimeout(300);

    // ---- 精简：候选为替换语义 ----
    mock.queueReply(CONDENSED);
    await page.getByTestId("scene-ai-condensing").click();
    await page.waitForSelector("[data-testid='ai-send-confirm']", { timeout: 15_000 });
    await page.getByTestId("ai-send-confirm-go").click();
    await page.waitForSelector("[data-testid='scene-candidate-review']", { timeout: 25_000 });
    const appendHintForCondensing = await page.getByTestId("scene-candidate-append-hint").count();
    check("精简候选不显示追加提示（保持替换语义）", appendHintForCondensing === 0, `hints=${appendHintForCondensing}`);
    await page.getByRole("button", { name: "丢弃候选" }).first().click();
    await page.waitForTimeout(300);
    const afterDiscard = bodyTextOf(await readBody());
    check("丢弃候选后正文不变", afterDiscard === afterAdopt, JSON.stringify(afterDiscard));

    // ---- 上下文边界：排除关联卡片后不得进入提示词 ----
    mock.queueReply(CONTINUATION);
    await page.getByTestId("scene-ai-continuation").click();
    await page.waitForSelector("[data-testid='ai-send-confirm']", { timeout: 15_000 });
    const cardsGroupLabel = await page.getByLabel("包含 关联卡片").count();
    check("发送确认提供「关联卡片」可排除分组", cardsGroupLabel === 1, `groups=${cardsGroupLabel}`);
    await page.getByLabel("包含 关联卡片").uncheck();
    await page.getByTestId("ai-send-confirm-go").click();
    await page.waitForSelector("[data-testid='scene-candidate-review']", { timeout: 25_000 });
    const excludedPrompt = mock.prompts[mock.prompts.length - 1] ?? "";
    check(
      "排除关联卡片后提示词不再包含卡片上下文（但仍含任务卡）",
      !excludedPrompt.includes("【关联卡片】") &&
        !excludedPrompt.includes("洛水之蔚（洛蔚）") &&
        excludedPrompt.includes("【任务卡】"),
      excludedPrompt.slice(0, 120)
    );
    await page.getByRole("button", { name: "丢弃候选" }).first().click();
    await page.waitForTimeout(300);
    const finalBody = bodyTextOf(await readBody());
    check("全程未确认的候选均未改写正文", finalBody === afterAdopt, JSON.stringify(finalBody));
    await page.screenshot({ path: path.join(evidenceDirectory, "scene-ai-context-exclusion.png") });
  } finally {
    await app.close().catch(() => undefined);
    await mock.close();
  }

  const failed = checks.filter((entry) => !entry.ok);
  console.log(`[stage4-scene-ai] ${checks.length - failed.length}/${checks.length} checks passed.`);
  if (failed.length > 0) process.exitCode = 1;
}

main().catch((error) => {
  console.error(error instanceof Error ? error.stack ?? error.message : String(error));
  process.exitCode = 1;
  process.exit(0);
});
