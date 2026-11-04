/**
 * 错误识别普查（WCAG 3.3.1 Error Identification，AA；顺带 3.3.3 Error Suggestion、
 * 4.1.3 Status Messages）。批次 BD-244 立。
 *
 * 立这一本的来由（运行时实测 /tmp/bd244-probe.log、/tmp/bd244-probe2.log）：
 *   ① 卡片类型编辑器两行字段，把第 2 行的「名称」留空 → 「每个字段都需要填写名称（label）。」
 *      把第 1 行的「名称」留空 → 同一句，一字不差。换位测试没换字，等于没指认出错项；
 *      并且全部 6 种失败态（A0～A5）测得「带 aria-invalid=true 的控件：0｜
 *      有 aria-describedby 且目标存在的：0」——读屏器停在被拦下的那格上什么也听不到。
 *   ② 关系类型编辑器：正向填好、只空反向，仍是那句「请填写正向名称和反向名称。」，
 *      把没出错的那格也点了名（R1 与 R2 文案相同）。
 *   ③ 大纲提示框（新建卷/新章/新场景/拆章/自定义编号）留空点「确定」或按回车：
 *      实测「❗ 没有出现任何错误文本」＋控件 invalid=- describedby=- ＋弹层停在原地。
 *      这是最坏的一种：按下去什么也没发生，用户只能猜。
 *
 * 判据（每个用例四条，任一不成立即红）：
 *   【有话说】拦下提交时必须出现可见错误文本（一条都没有 → 红）。
 *   【能被播报】错误文本落在 role=alert／role=status／aria-live 里（否则 4.1.3 红）。
 *   【指认得出】用例声明的出错控件必须 aria-invalid="true"，且 aria-describedby 指向的
 *      元素（可多个，空格分隔）里有一个的文本就是那条错误文本。没出错控件被牵连也算红，
 *      所以同站点里「不该亮」的控件也一并声明（mustNot）。
 *   【换位要换字】站点开了 swap 时，各用例的错误文本必须两两不同——同一句文案盖不住
 *      不同失败项，这正是 ① ② 两条实测缺陷的形状。
 *
 * 站点表里的两条绿基线（新建作品向导、查找替换）是本仓自家既有写法
 * （CreateProjectWizard.tsx:205-210、ReplacePanel.tsx:227-228：文案 + aria-invalid +
 * aria-describedby → 错误元素 id）。留着它们，判据就不只是围着本批那三处修好的地方转。
 *
 * 需要 Xvfb 与已构建的 out/：
 *   timeout 900 xvfb-run -a --server-args="-screen 0 1600x1000x24" node scripts/audit-error-identification.mjs
 * 退出码：有红 → ERROR_IDENT_RED / exit 1；站点或用例被跳过 → FOCUS_RING_SKIP 式非零退出。
 */
import { mkdtempSync } from "node:fs";
import { tmpdir, platform } from "node:os";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { _electron as electron } from "playwright-core";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(__dirname, "..");
const profileDir = mkdtempSync(path.join(tmpdir(), "creation-errorident-"));
const electronPath = path.join(root, "node_modules", "electron", "dist", platform() === "win32" ? "electron.exe" : "electron");
const mainJs = path.join(root, "out", "main", "index.js");

const app = await electron.launch({
  executablePath: electronPath,
  args: [mainJs, "--disable-dev-shm-usage"],
  cwd: root,
  env: { ...process.env, CREATION_READER_CAPTURE_PROFILE: profileDir }
});
const page = await app.firstWindow();
await page.waitForLoadState("domcontentloaded");
await page.waitForSelector(".desktop-canvas", { timeout: 30000 });
await page.waitForTimeout(600);

const gotoSidebar = async (label) => {
  await page.locator(".desktop-sidebar button", { hasText: label }).first().click();
  await page.waitForTimeout(400);
};

const openWorkbench = async () => {
  await gotoSidebar("项目");
  const back = page.locator(".project-nav-back").first();
  if (await back.isVisible().catch(() => false)) await back.click();
  await page.waitForSelector(".project-home", { timeout: 15000 });
  const workbench = page.locator(".project-workbench");
  if (await workbench.isVisible().catch(() => false)) return;
  const createFirst = page.getByRole("button", { name: "新建第一个项目" }).first();
  if (await createFirst.isVisible().catch(() => false)) await createFirst.click();
  else await page.getByRole("button", { name: /新建项目/ }).first().click();
  const wizard = page.locator("[role=dialog]").first();
  await wizard.waitFor({ state: "visible", timeout: 15000 });
  await wizard.getByRole("button", { name: /下一步/ }).click();
  await wizard.locator("input[placeholder='例如：测试项目']").first().fill("错误识别普查项目");
  await wizard.getByRole("button", { name: /下一步/ }).click();
  await page.locator("input[placeholder='例如：500000']").first().fill("50000");
  await wizard.getByRole("button", { name: /创建项目/ }).click();
  await page.waitForTimeout(1500);
};

const view = async (label, selector) => {
  await openWorkbench();
  await page.keyboard.press("Escape").catch(() => {});
  await page.locator(".project-nav button", { hasText: label }).first().click();
  await page.waitForSelector(selector, { timeout: 20000 });
  await page.waitForTimeout(400);
};

/** 打开「管理卡片类型」弹层并铺好两行合法字段（每站点开前重铺，避免上一站的残留）。 */
const openCardTypeDialog = async () => {
  await view("设定卡", ".cards-page");
  await page.getByRole("button", { name: /卡片类型/ }).first().click();
  const dialog = page.locator("[role=dialog]").first();
  await dialog.waitFor({ state: "visible", timeout: 15000 });
  await page.waitForTimeout(400);
  await dialog.locator("input[aria-label='类型名称']").fill("探针类型");
  const add = dialog.getByRole("button", { name: /添加字段/ }).first();
  const rowCount = async () => dialog.locator(".cte-field-row").count();
  while ((await rowCount()) < 2) await add.click();
  while ((await rowCount()) > 2) {
    await dialog.locator('button[title="删除字段"]').last().click();
    await page.waitForTimeout(150);
  }
  await page.waitForTimeout(200);
  const row = (n) => dialog.locator(".cte-field-row").nth(n - 1);
  await row(1).locator("input[aria-label='字段 1 名称']").fill("字段甲");
  await row(1).locator("input[aria-label='字段 1 稳定 key']").fill("k1");
  await row(2).locator("input[aria-label='字段 2 名称']").fill("字段乙");
  await row(2).locator("input[aria-label='字段 2 稳定 key']").fill("k2");
  await row(1).locator("select[aria-label='字段 1 类型']").selectOption("text");
  await row(2).locator("select[aria-label='字段 2 类型']").selectOption("text");
  await page.waitForTimeout(200);
  return dialog;
};

const createType = async (dialog) => {
  await dialog.getByRole("button", { name: /创建类型/ }).first().click();
  await page.waitForTimeout(350);
};

/**
 * 量一个用例：在 scope 里取可见错误文本，并核对声明的出错控件（含牵连检查）。
 * 页面里跑的函数不能引用 Node 侧的东西（页边序列化，BD-243 踩过 idOf 的坑）。
 */
const probe = (scopeSel, controlSels, notSels) =>
  page.evaluate(
    ([sel, ctrls, notCtrls]) => {
      const scope = document.querySelector(sel) || document.body;
      const visible = (el) => {
        const r = el.getBoundingClientRect();
        const cs = getComputedStyle(el);
        return r.width > 1 && r.height > 1 && cs.visibility !== "hidden" && cs.opacity !== "0";
      };
      const liveKindOf = (el) => {
        let n = el;
        for (let i = 0; n && i < 8; i += 1, n = n.parentElement) {
          const lr = n.getAttribute("aria-live") || n.getAttribute("role");
          if (lr === "assertive" || lr === "polite" || lr === "alert" || lr === "status") return `${n.tagName.toLowerCase()}[${lr}]`;
        }
        return "";
      };
      const errorEls = [...scope.querySelectorAll(".cards-form-error, .creation-field-error, .replace-error-banner, [role=alert]")]
        .filter((el) => visible(el) && (el.textContent || "").trim().length > 1);
      const message = errorEls.length ? (errorEls[0].textContent || "").trim() : "";
      const live = errorEls.length ? liveKindOf(errorEls[0]) : "";
      const errorIds = new Set(errorEls.map((el) => el.id).filter(Boolean));
      const readOne = (cs) => {
        const el = scope.querySelector(cs) || document.querySelector(cs);
        if (!el) return { sel: cs, found: false, invalid: "", describedTexts: [], hitsError: false };
        const invalid = el.getAttribute("aria-invalid") || "";
        const db = el.getAttribute("aria-describedby") || "";
        const texts = db
          ? db
            .split(/\s+/)
            .map((id) => {
              const t = document.getElementById(id);
              return t ? (t.textContent || "").trim() : "";
            })
            .filter(Boolean)
          : [];
        const hits = db
          ? db.split(/\s+/).some((id) => {
            const t = document.getElementById(id);
            return !!t && (errorIds.has(id) || (message !== "" && (t.textContent || "").trim() === message));
          })
          : false;
        return { sel: cs, found: true, invalid, describedTexts: texts, hitsError: hits };
      };
      return {
        message,
        live,
        controls: ctrls.map(readOne),
        notControls: notCtrls.map(readOne)
      };
    },
    [scopeSel, controlSels, notSels]
  );

/**
 * 站点表：每站 prep（进页面/铺数据）+ cases（每例一条失败态）。
 * controls = 该失败态里「必须亮错误」的控件；mustNot = 同站内「不该亮」的控件（牵连检查）。
 * swap=true 的站点要求各例文案两两不同（换位测试）。
 */
const SITES = [
  {
    label: "卡片类型·字段名称留空（换位测试）",
    scope: "[role=dialog]",
    swap: true,
    prep: openCardTypeDialog,
    cases: [
      {
        name: "第 2 行的名称留空",
        act: async (dialog) => {
          await dialog.locator("input[aria-label='字段 2 名称']").fill("");
          await createType(dialog);
        },
        controls: ["input[aria-label='字段 2 名称']"],
        mustNot: ["input[aria-label='字段 1 名称']", "input[aria-label='类型名称']"]
      },
      {
        name: "第 1 行的名称留空",
        act: async (dialog) => {
          await dialog.locator("input[aria-label='字段 2 名称']").fill("字段乙");
          await dialog.locator("input[aria-label='字段 1 名称']").fill("");
          await createType(dialog);
        },
        controls: ["input[aria-label='字段 1 名称']"],
        mustNot: ["input[aria-label='字段 2 名称']"]
      }
    ]
  },
  {
    label: "卡片类型·字段 key 不合法",
    scope: "[role=dialog]",
    prep: openCardTypeDialog,
    cases: [
      {
        name: "第 2 行 key 以数字开头",
        act: async (dialog) => {
          await dialog.locator("input[aria-label='字段 2 稳定 key']").fill("2bad");
          await createType(dialog);
        },
        controls: ["input[aria-label='字段 2 稳定 key']"],
        mustNot: ["input[aria-label='字段 1 稳定 key']"]
      }
    ]
  },
  {
    label: "卡片类型·字段 key 重复",
    scope: "[role=dialog]",
    prep: openCardTypeDialog,
    cases: [
      {
        name: "第 2 行照抄第 1 行 key",
        act: async (dialog) => {
          await dialog.locator("input[aria-label='字段 2 稳定 key']").fill("k1");
          await createType(dialog);
        },
        controls: ["input[aria-label='字段 2 稳定 key']"]
      }
    ]
  },
  {
    label: "卡片类型·单选字段没有选项",
    scope: "[role=dialog]",
    prep: openCardTypeDialog,
    cases: [
      {
        name: "第 2 行改成单选、选项留空",
        act: async (dialog) => {
          await dialog.locator("select[aria-label='字段 2 类型']").selectOption("select");
          await page.waitForTimeout(250);
          await dialog.locator("textarea[aria-label='字段 2 选项']").fill("");
          await createType(dialog);
        },
        controls: ["textarea[aria-label='字段 2 选项']"]
      }
    ]
  },
  {
    label: "卡片类型·类型名称留空",
    scope: "[role=dialog]",
    prep: openCardTypeDialog,
    cases: [
      {
        name: "类型名称清空",
        act: async (dialog) => {
          await dialog.locator("input[aria-label='类型名称']").fill("");
          await createType(dialog);
        },
        controls: ["input[aria-label='类型名称']"],
        mustNot: ["input[aria-label='字段 1 名称']"]
      }
    ]
  },
  {
    label: "关系类型·缺项点名（换位测试）",
    scope: "[role=dialog]",
    swap: true,
    prep: async () => {
      await view("设定卡", ".cards-page");
      await page.getByRole("button", { name: /关系类型/ }).first().click();
      const dialog = page.locator("[role=dialog]").first();
      await dialog.waitFor({ state: "visible", timeout: 15000 });
      await page.waitForTimeout(400);
      return dialog;
    },
    cases: [
      {
        name: "只空反向名称",
        act: async (dialog) => {
          await dialog.locator("input[aria-label='正向名称']").fill("师父的");
          await dialog.locator("input[aria-label='反向名称']").fill("");
          await dialog.getByRole("button", { name: /创建关系类型/ }).first().click();
          await page.waitForTimeout(350);
        },
        controls: ["input[aria-label='反向名称']"],
        mustNot: ["input[aria-label='正向名称']"]
      },
      {
        name: "只空正向名称",
        act: async (dialog) => {
          await dialog.locator("input[aria-label='反向名称']").fill("是师父");
          await dialog.locator("input[aria-label='正向名称']").fill("");
          await dialog.getByRole("button", { name: /创建关系类型/ }).first().click();
          await page.waitForTimeout(350);
        },
        controls: ["input[aria-label='正向名称']"],
        mustNot: ["input[aria-label='反向名称']"]
      },
      {
        name: "两个都空",
        act: async (dialog) => {
          await dialog.locator("input[aria-label='正向名称']").fill("");
          await dialog.locator("input[aria-label='反向名称']").fill("");
          await dialog.getByRole("button", { name: /创建关系类型/ }).first().click();
          await page.waitForTimeout(350);
        },
        controls: ["input[aria-label='正向名称']", "input[aria-label='反向名称']"]
      }
    ]
  },
  {
    label: "大纲·新建卷提示框留空",
    scope: ".motion-dialog",
    prep: async () => {
      await view("大纲", ".outline-page");
      await page.getByRole("button", { name: /新建卷/ }).first().click();
      await page.waitForSelector(".motion-dialog", { timeout: 15000 });
      await page.waitForTimeout(400);
      return page.locator(".motion-dialog").first();
    },
    cases: [
      {
        name: "清空名称后点「确定」",
        act: async (box) => {
          await box.locator("input").fill("");
          await box.getByRole("button", { name: /^确定$/ }).first().click();
          await page.waitForTimeout(350);
        },
        controls: [".motion-dialog input"]
      },
      {
        name: "清空名称后按回车",
        act: async (box) => {
          await box.locator("input").fill("");
          await box.locator("input").press("Enter");
          await page.waitForTimeout(350);
        },
        controls: [".motion-dialog input"]
      }
    ]
  },
  {
    label: "绿基线·新建作品向导标题留空（自家既有形状）",
    scope: "[role=dialog]",
    prep: async () => {
      await gotoSidebar("项目");
      const back = page.locator(".project-nav-back").first();
      if (await back.isVisible().catch(() => false)) await back.click();
      await page.waitForSelector(".project-home", { timeout: 15000 });
      await page.getByRole("button", { name: /新建项目/ }).first().click();
      const dialog = page.locator("[role=dialog]").first();
      await dialog.waitFor({ state: "visible", timeout: 15000 });
      await dialog.getByRole("button", { name: /下一步/ }).click();
      await page.waitForTimeout(400);
      return dialog;
    },
    cases: [
      {
        name: "标题写了又清空，再离开这一格",
        act: async (dialog) => {
          const title = dialog.locator("input[placeholder='例如：测试项目']").first();
          await title.fill("临时名字");
          await title.fill("");
          await title.press("Tab");
          await page.waitForTimeout(350);
        },
        controls: ["input[placeholder='例如：测试项目']"]
      }
    ]
  },
  {
    label: "绿基线·查找替换空查找（自家既有形状）",
    scope: ".replace-panel",
    prep: async () => {
      await view("写作", ".writing-desk");
      const btn = page.locator('button[aria-label="查找替换"]').first();
      await btn.click();
      await page.waitForSelector(".replace-panel", { timeout: 15000 });
      await page.waitForTimeout(400);
      return page.locator(".replace-panel").first();
    },
    cases: [
      {
        name: "查找内容留空点「预览替换」",
        act: async () => {
          await page.locator('[data-testid="replace-preview"]').first().click();
          await page.waitForTimeout(450);
        },
        controls: ['[data-testid="replace-find"]']
      }
    ]
  }
];

const reds = [];
const skipped = [];
let caseCount = 0;
let greenCases = 0;

for (const site of SITES) {
  let handle = null;
  try {
    handle = await site.prep();
  } catch (error) {
    skipped.push(`${site.label}：进不去（${String(error && error.message || error).split("\n")[0].slice(0, 90)}）`);
    continue;
  }
  const texts = [];
  for (const kase of site.cases) {
    caseCount += 1;
    try {
      await kase.act(handle);
    } catch (error) {
      skipped.push(`${site.label}／${kase.name}：触发动作失败（${String(error && error.message || error).split("\n")[0].slice(0, 90)}）`);
      continue;
    }
    const r = await probe(site.scope, kase.controls, kase.mustNot || []);
    texts.push(r.message);
    const where = `${site.label}／${kase.name}`;
    let bad = "";
    if (!r.message) bad = "拦下了却没有可见错误文本";
    else if (!r.live) bad = `错误文本不在可播报区（${r.message.slice(0, 40)}）`;
    for (const c of r.controls) {
      if (bad) break;
      if (!c.found) bad = `找不到声明的出错控件 ${c.sel}`;
      else if (c.invalid !== "true") bad = `${c.sel} 没有 aria-invalid="true"（实测 aria-invalid="${c.invalid || "空"}"）`;
      else if (!c.hitsError) bad = `${c.sel} 的 aria-describedby 指不到那条错误元素（describedby 文本：${c.describedTexts.join(" / ").slice(0, 60) || "无"}）`;
    }
    for (const c of r.notControls) {
      if (bad) break;
      if (c.found && c.invalid === "true") bad = `没出错的 ${c.sel} 也被标成 aria-invalid（牵连）`;
    }
    if (bad) reds.push(`${where} → ${bad}`);
    else greenCases += 1;
  }
  if (site.swap) {
    const seen = texts.filter(Boolean);
    if (seen.length >= 2 && new Set(seen).size !== seen.length) {
      reds.push(`${site.label} → 换位测试没换字：${seen.map((t) => `「${t.slice(0, 34)}」`).join(" == ")}`);
    }
  }
  await page.keyboard.press("Escape").catch(() => {});
  await page.waitForTimeout(300);
}

console.log(`错误识别普查：站点 ${SITES.length} 个、用例 ${caseCount} 个（绿 ${greenCases}）`);
for (const r of reds) console.log(`  ❌ ${r}`);
for (const s of skipped) console.log(`  ⚠️ 跳过 ${s}`);
if (reds.length > 0) {
  console.log("ERROR_IDENT_RED 错误识别有红点");
  await app.close();
  process.exit(1);
}
if (skipped.length > 0) {
  console.log(`ERROR_IDENT_SKIP 有 ${skipped.length} 项被跳过`);
  await app.close();
  process.exit(2);
}
console.log("ERROR_IDENT_OK 每个失败态都点得出出错项、控件挂得上、文案换位会变");
await app.close();
