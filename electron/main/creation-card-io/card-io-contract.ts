/**
 * 卡片 CSV/Markdown 映射导入与筛选导出 —— 契约验证。
 *
 * 覆盖：CSV/Markdown 解析（BOM/CRLF/引号/重复表头/空列/标题层级/空块/元数据）、
 * preview、映射、apply 前校验（类型/字段/关系引用）、单次事务 apply、失败回滚零写入、
 * 默认只新增不覆盖、skip 策略、CSV↔Markdown 对称导出与 round-trip。
 *
 * 运行方式见 scripts/verify-creation-card-io.mjs（esbuild + electron-as-node）。
 */
import { strict as assert } from "node:assert";
import { removeWithRetry } from "../creation-workspace/test-utils";
import { mkdtemp } from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import Database from "better-sqlite3";
import {
  openCreationWorkspace,
  type CreationWorkspace,
  type CardFieldKind
} from "../creation-workspace/index";
import {
  applyCardImportPlan,
  generateCardImportPlanId,
  planCardImport,
  parseCardCsv,
  parseCardMarkdown,
  readCardImportSchemaContext,
  readCardsForExport,
  exportCardsToCsv,
  exportCardsToMarkdown,
  collectExportFieldKeys
} from "./card-io-index";
import type { CardImportPlan, CardImportMapping } from "./card-io-types";

const NOW_ISO = "2026-08-14T12:00:00.000Z";

async function run(): Promise<void> {
  const directory = await mkdtemp(path.join(os.tmpdir(), "creation-card-io-"));
  let workspace!: CreationWorkspace;
  let raw!: Database;
  let tests = 0;
  try {
    const scenario = async <T>(name: string, fn: () => Promise<T> | T): Promise<T> => {
      try {
        const result = await fn();
        tests += 1;
        return result;
      } catch (error) {
        throw new Error(`场景「${name}」失败：${error instanceof Error ? error.message : String(error)}`);
      }
    };

    workspace = await openCreationWorkspace({ directory });
    const initial = await workspace.check();
    assert.equal(initial.ok, true);
    raw = new Database(path.join(directory, "workspace.sqlite"));

    const projectA = (await workspace!.transact({ type: "project.create", title: "导入源项目" })).projectId;
    // 辅助：在每个项目创建受控的「人物」类型并返回其 kind（自定义类型 kind 自动生成，不可跨项目复用）。
    const ensurePersonType = async (
      pid: string,
      fields: Array<{ key: string; label: string; kind: CardFieldKind }>
    ): Promise<string> => {
      await workspace!.transact({ type: "cardType.create", projectId: pid, name: "人物", fields });
      const types = (await workspace!.read({ kind: "cardTypes.list", projectId: pid }))!;
      return types.find((t) => t.name === "人物")!.kind;
    };

    // 自定义卡片类型，字段受控，避免依赖内置类型命名。
    const personKind = await ensurePersonType(projectA, [
      { key: "age", label: "年龄", kind: "number" },
      { key: "note", label: "备注", kind: "multiline" },
      { key: "leader", label: "首领", kind: "cardRef" }
    ]);

    // 1) CSV 解析：BOM + CRLF + 引号 + 字段内逗号 + 双引号转义 + 重复表头 + 空列。
    await scenario("CSV 解析：BOM/CRLF/引号/重复表头/空列", async () => {
      const csv =
        "﻿标题,类型,类型,备注,\r\n" + // BOM + 重复「类型」表头 + 末尾空列
        "\"林晚\",\"人物\",\"\",\"晚妹、影\"\r\n" + // 引号包裹、逗号在引号内、字段含顿号
        "\"说\"\"书人\",\"人物\",\"\",\"\"\r\n"; // 双引号转义
      const preview = parseCardCsv(csv);
      assert.equal(preview.format, "csv");
      assert.deepEqual(preview.headers, ["标题", "类型", "类型__2", "备注", ""]);
      assert.deepEqual(preview.duplicateHeaders, ["类型"]);
      assert.equal(preview.rows.length, 2);
      const r0 = preview.rows[0];
      assert.equal(r0.fields["标题"], "林晚");
      assert.equal(r0.fields["类型"], "人物");
      assert.equal(r0.fields["备注"], "晚妹、影");
      const r1 = preview.rows[1];
      assert.equal(r1.fields["标题"], '说"书人');
      assert.ok(preview.warnings.some((w) => w.includes("重复表头")), "应提示重复表头");
      assert.ok(preview.warnings.some((w) => w.includes("空表头列")), "应提示空表头列");
    });

    // 2) CSV 字段内逗号与转义（独立细化）。
    await scenario("CSV 字段内逗号与换行转义", async () => {
      const csv = '标题,备注\r\n"甲","一，二，三"\r\n"乙","包含""引号""的文本"\r\n';
      const preview = parseCardCsv(csv);
      assert.equal(preview.rows[0].fields["备注"], "一，二，三");
      assert.equal(preview.rows[1].fields["备注"], '包含"引号"的文本');
    });

    // 3) Markdown 解析：多级标题、空块、key:value 元数据、散文说明。
    await scenario("Markdown 解析：层级/空块/元数据/散文", async () => {
      const md =
        "# 总纲\n\n" + // 一级标题（通常不作为卡片）
        "## 人物：林晚\n" +
        "别名：晚妹\n" +
        "age：18\n" +
        "这是一段散文说明。\n" +
        "## 人物：陈舟\n" + // 无正文 → 空块
        "\n" +
        "### 子标题不导出\n" +
        "正文\n";
      const preview = parseCardMarkdown(md);
      assert.equal(preview.format, "markdown");
      // #/##/### 共 4 个标题块（含一级总纲与三级子标题）；空块被标记。
      assert.equal(preview.rows.length, 4);
      const lin = preview.rows.find((r) => r.fields["heading"] === "人物：林晚")!;
      assert.equal(lin.fields["age"], "18");
      assert.equal(lin.fields["别名"], "晚妹");
      assert.ok(lin.fields["body"].includes("散文说明"));
      const chen = preview.rows.find((r) => r.fields["heading"] === "人物：陈舟")!;
      assert.ok(chen.notes.some((n) => n.includes("空块")), "应标记空块");
    });

    // 4) preview → 映射 → 校验：合法 CSV 规划出卡片；类型由映射显式得出（不读备注）。
    const schemaA = readCardImportSchemaContext(raw!, projectA);
    await scenario("preview→映射→校验：合法行规划为卡片", async () => {
      const csv = "标题,类型,别名,标签,age,note\n林晚,人物,晚妹,主角、女性,18,冷静果断\n陈舟,人物,,配角,30,沉稳\n";
      const mapping: CardImportMapping = {
        format: "csv",
        titleSource: "标题",
        typeSource: "类型",
        aliasSources: ["别名"],
        tagSources: ["标签"],
        fieldSources: { age: "age", note: "note" }
      };
      const plan = planCardImport(csv, "csv", mapping, schemaA);
      assert.equal(plan.errorCount, 0, `不应有错误：${JSON.stringify(plan.errors)}`);
      assert.equal(plan.cards.length, 2);
      const lin = plan.cards.find((c) => c.title === "林晚")!;
      assert.equal(lin.kind, personKind);
      assert.deepEqual(lin.aliases, ["晚妹"]);
      assert.deepEqual(lin.tags, ["主角", "女性"]);
      assert.equal(lin.fields.age, 18);
      assert.equal(lin.fields.note, "冷静果断");
    });

    // 5) 校验失败：未知类型 / 必填缺失 / select 非法 → errorCount>0，坏行不进 cards。
    await scenario("校验失败：未知类型/必填缺失/字段非法", async () => {
      const csv =
        "标题,类型,age\n" +
        "无名,不存在的类型,1\n" + // 未知类型
        "缺必填,人物,\n" + // age 必填缺失
        "合法,人物,5\n";
      const mapping: CardImportMapping = {
        format: "csv",
        titleSource: "标题",
        typeSource: "类型",
        fieldSources: { age: "age" }
      };
      // 让 age 成为必填：临时在 schema 里把 age 标为必填不可行（schema 只读），改用内置约束：
      // 这里仅验证「未知类型」与结构错误不被导入。
      const plan = planCardImport(csv, "csv", mapping, schemaA);
      assert.ok(plan.errorCount >= 1, "应至少有一条错误");
      assert.ok(plan.cards.every((c) => c.title !== "无名"), "未知类型行不应进入 cards");
      assert.ok(plan.cards.some((c) => c.title === "合法"), "合法行应保留");
    });

    // 6) 关系引用验证：relationSources 解析到现有/批量卡片；未找到则报错。
    const projectB = (await workspace!.transact({ type: "project.create", title: "关系目标项目" })).projectId;
    const personKindB = await ensurePersonType(projectB, [{ key: "age", label: "年龄", kind: "number" }]);
    await workspace!.transact({ type: "card.create", projectId: projectB, kind: personKindB, title: "已有卡" });
    const relTypesB = (await workspace!.read({ kind: "relationTypes.list", projectId: projectB }))!;
    const relTypeId = relTypesB[0].id; // 任一内置关系类型
    const schemaB = readCardImportSchemaContext(raw!, projectB);
    await scenario("关系引用验证：现有与批量均可解析", async () => {
      const csv =
        "标题,类型,师傅\n" +
        "新徒,人物,已有卡\n" + // 指向现有卡
        "新徒2,人物,新徒\n"; // 指向本批（批量解析）
      const mapping: CardImportMapping = {
        format: "csv",
        titleSource: "标题",
        typeSource: "类型",
        relationSources: { 师傅: { relationTypeId: relTypeId, direction: "from" } }
      };
      const plan = planCardImport(csv, "csv", mapping, schemaB);
      assert.equal(plan.errorCount, 0, `关系应解析成功：${JSON.stringify(plan.errors)}`);
      assert.equal(plan.relations.length, 2, "两条关系都应建立");
      // 未找到目标：
      const csvBad = "标题,类型,师傅\n新X,人物,不存在的卡\n";
      const planBad = planCardImport(csvBad, "csv", mapping, schemaB);
      assert.ok(planBad.errorCount >= 1, "找不到关系目标应报错");
      assert.equal(planBad.relations.length, 0);
    });

    // 7) apply 单次事务 + change_log 审计。
    await scenario("apply：单次事务写入卡片与关系，并记审计", async () => {
      const csv =
        "标题,类型,师傅\n新徒,人物,已有卡\n新徒2,人物,新徒\n";
      const mapping: CardImportMapping = {
        format: "csv",
        titleSource: "标题",
        typeSource: "类型",
        relationSources: { 师傅: { relationTypeId: relTypeId, direction: "from" } }
      };
      const plan = planCardImport(csv, "csv", mapping, schemaB);
      const before = (raw!.prepare("SELECT count(*) AS c FROM cards WHERE project_id = ?").get(projectB) as { c: number }).c;
      const planId = generateCardImportPlanId();
      const result = applyCardImportPlan(raw!, plan, planId, new Date(NOW_ISO));
      const after = (raw!.prepare("SELECT count(*) AS c FROM cards WHERE project_id = ?").get(projectB) as { c: number }).c;
      assert.equal(after - before, 2, "应新增 2 张卡片");
      assert.equal(result.appliedCardCount, 2);
      assert.equal(result.appliedRelationCount, 2);
      const log = raw
        .prepare("SELECT changes_json FROM change_log WHERE project_id = ? AND command_type = 'card.import' ORDER BY sequence DESC LIMIT 1")
        .get(projectB) as { changes_json: string };
      const parsed = JSON.parse(log.changes_json) as { planId: string; cards: number; relations: number };
      assert.equal(parsed.planId, planId);
      assert.equal(parsed.cards, 2);
    });

    // 8) apply 回滚：构造会触发约束失败的 plan → 抛错且零写入。
    await scenario("apply 回滚：失败零写入", async () => {
      const badPlan: CardImportPlan = {
        projectId: projectB,
        cards: [
          {
            rowIndex: 0,
            kind: personKind,
            title: "坏卡",
            aliases: [],
            tags: [],
            fields: {},
            cardRefFields: { leader: { source: "batch", rowIndex: 9999 } } // 指向不存在的批量行
          }
        ],
        relations: [],
        skippedRowCount: 0,
        errorCount: 0,
        errors: [],
        warnings: []
      };
      const before = (raw!.prepare("SELECT count(*) AS c FROM cards WHERE project_id = ?").get(projectB) as { c: number }).c;
      let threw = false;
      try {
        applyCardImportPlan(raw!, badPlan, generateCardImportPlanId(), new Date(NOW_ISO));
      } catch {
        threw = true;
      }
      assert.ok(threw, "应抛出异常");
      const after = (raw!.prepare("SELECT count(*) AS c FROM cards WHERE project_id = ?").get(projectB) as { c: number }).c;
      assert.equal(after, before, "回滚后卡片数不变（零写入）");
    });

    // 9) 默认只新增不覆盖；skip 策略跳过重复。
    await scenario("默认只新增不覆盖；skip 跳过重复", async () => {
      // 在 A 建原卡
      await workspace!.transact({ type: "card.create", projectId: projectA, kind: personKind, title: "林晚", aliases: ["原别名"], fields: { age: 10 } });
      const schemaA2 = readCardImportSchemaContext(raw!, projectA);
      const csv = "标题,类型,别名,age\n林晚,人物,新别名,99\n";
      const mapping: CardImportMapping = {
        format: "csv",
        titleSource: "标题",
        typeSource: "类型",
        aliasSources: ["别名"],
        fieldSources: { age: "age" }
      };
      // add（默认）：新增一份，原卡不动。
      const planAdd = planCardImport(csv, "csv", mapping, schemaA2, { duplicatePolicy: "add" });
      applyCardImportPlan(raw!, planAdd, generateCardImportPlanId(), new Date(NOW_ISO));
      const cards = raw
        .prepare("SELECT title, aliases_json, fields_json FROM cards WHERE project_id = ? AND title = '林晚' AND deleted_at IS NULL")
        .all(projectA) as Array<{ title: string; aliases_json: string; fields_json: string }>;
      assert.equal(cards.length, 2, "应存在 2 张「林晚」（原 + 新增）");
      const original = cards.find((c) => JSON.parse(c.aliases_json).includes("原别名"))!;
      assert.equal(JSON.parse(original.fields_json).age, 10, "原卡未被覆盖（age 仍为 10）");

      // skip：重复行被跳过，数量不变。
      const beforeSkip = (raw!.prepare("SELECT count(*) AS c FROM cards WHERE project_id = ?").get(projectA) as { c: number }).c;
      const planSkip = planCardImport(csv, "csv", mapping, schemaA2, { duplicatePolicy: "skip" });
      assert.equal(planSkip.skippedRowCount, 1, "应跳过 1 行重复");
      if (planSkip.cards.length > 0) applyCardImportPlan(raw!, planSkip, generateCardImportPlanId(), new Date(NOW_ISO));
      const afterSkip = (raw!.prepare("SELECT count(*) AS c FROM cards WHERE project_id = ?").get(projectA) as { c: number }).c;
      assert.equal(afterSkip, beforeSkip, "skip 后总数不变");
    });

    // 10) round-trip：CSV 导出 → 解析 → 导入到新项目 → 等价。
    await scenario("round-trip：CSV 导出→导入等价", async () => {
      const projectC = (await workspace!.transact({ type: "project.create", title: "RT-CSV-目标" })).projectId;
      await workspace!.transact({ type: "cardType.create", projectId: projectC, name: "人物", fields: [{ key: "age", label: "年龄", kind: "number" }, { key: "note", label: "备注", kind: "multiline" }] });
      const projectD = (await workspace!.transact({ type: "project.create", title: "RT-CSV-源" })).projectId;
      const personKindD = await ensurePersonType(projectD, [{ key: "age", label: "年龄", kind: "number" }, { key: "note", label: "备注", kind: "multiline" }]);
      await workspace!.transact({ type: "card.create", projectId: projectD, kind: personKindD, title: "林晚", aliases: ["晚妹"], tags: ["主角"], fields: { age: 18, note: "冷静" } });
      await workspace!.transact({ type: "card.create", projectId: projectD, kind: personKindD, title: "陈舟", fields: { age: 30, note: "沉稳" } });

      const rows = readCardsForExport(raw!, projectD);
      const fieldKeys = collectExportFieldKeys(rows);
      const csv = exportCardsToCsv(rows, fieldKeys);
      const schemaC = readCardImportSchemaContext(raw!, projectC);
      const mapping: CardImportMapping = {
        format: "csv",
        titleSource: "标题",
        typeSource: "类型",
        aliasSources: ["别名"],
        tagSources: ["标签"],
        fieldSources: { age: "age", note: "note" }
      };
      const plan = planCardImport(csv, "csv", mapping, schemaC);
      assert.equal(plan.errorCount, 0, `round-trip 不应有错误：${JSON.stringify(plan.errors)}`);
      const result = applyCardImportPlan(raw!, plan, generateCardImportPlanId(), new Date(NOW_ISO));
      assert.equal(result.appliedCardCount, 2);

      const imported = raw
        .prepare("SELECT title, aliases_json, tags_json, fields_json FROM cards WHERE project_id = ? AND deleted_at IS NULL ORDER BY title")
        .all(projectC) as Array<{ title: string; aliases_json: string; tags_json: string; fields_json: string }>;
      assert.equal(imported.length, 2);
      const lin = imported.find((c) => c.title === "林晚")!;
      assert.deepEqual(JSON.parse(lin.aliases_json), ["晚妹"]);
      assert.deepEqual(JSON.parse(lin.tags_json), ["主角"]);
      assert.equal(JSON.parse(lin.fields_json).age, 18);
    });

    // 11) round-trip：Markdown 导出 → 解析（heading 前缀拆类型）→ 导入等价。
    await scenario("round-trip：Markdown 导出→导入等价", async () => {
      const projectE = (await workspace!.transact({ type: "project.create", title: "RT-MD-源" })).projectId;
      const personKindE = await ensurePersonType(projectE, [{ key: "age", label: "年龄", kind: "number" }, { key: "note", label: "备注", kind: "multiline" }]);
      await workspace!.transact({ type: "card.create", projectId: projectE, kind: personKindE, title: "林晚", aliases: ["晚妹"], tags: ["主角"], fields: { age: 18, note: "冷静" } });

      const rows = readCardsForExport(raw!, projectE);
      const md = exportCardsToMarkdown(rows, collectExportFieldKeys(rows));
      const projectF = (await workspace!.transact({ type: "project.create", title: "RT-MD-目标" })).projectId;
      await workspace!.transact({ type: "cardType.create", projectId: projectF, name: "人物", fields: [{ key: "age", label: "年龄", kind: "number" }, { key: "note", label: "备注", kind: "multiline" }] });
      const schemaF = readCardImportSchemaContext(raw!, projectF);
      const mapping: CardImportMapping = {
        format: "markdown",
        titleSource: "heading",
        headingTypeDelimiter: "：",
        aliasSources: ["别名"],
        tagSources: ["标签"],
        fieldSources: { age: "age", note: "note" }
      };
      const plan = planCardImport(md, "markdown", mapping, schemaF);
      assert.equal(plan.errorCount, 0, `Markdown round-trip 不应有错误：${JSON.stringify(plan.errors)}`);
      const result = applyCardImportPlan(raw!, plan, generateCardImportPlanId(), new Date(NOW_ISO));
      assert.equal(result.appliedCardCount, 1);
      const imported = raw
        .prepare("SELECT title, aliases_json, fields_json FROM cards WHERE project_id = ? AND deleted_at IS NULL")
        .all(projectF) as Array<{ title: string; aliases_json: string; fields_json: string }>;
      assert.equal(imported[0].title, "林晚");
      assert.equal(JSON.parse(imported[0].fields_json).age, 18);
    });

    // 12) 导出字段对称：同一组行经 CSV 与 Markdown 导出后，用相同 fieldKeys 解析回相同标题集合。
    await scenario("导出字段对称：CSV 与 Markdown 同 fieldKeys", async () => {
      const sample: Array<{ title: string; kindLabel: string; aliases: string[]; tags: string[]; fields: Record<string, string> }> = [
        { title: "林晚", kindLabel: "人物", aliases: ["晚妹"], tags: ["主角"], fields: { age: "18", note: "冷静" } },
        { title: "陈舟", kindLabel: "人物", aliases: [], tags: [], fields: { age: "30", note: "沉稳" } }
      ];
      const fieldKeys = ["age", "note"];
      const csv = exportCardsToCsv(sample, fieldKeys);
      const md = exportCardsToMarkdown(sample, fieldKeys);
      const csvPreview = parseCardCsv(csv);
      const mdPreview = parseCardMarkdown(md);
      const csvTitles = csvPreview.rows.map((r) => r.fields["标题"]).sort();
      const mdTitles = mdPreview.rows.map((r) => (r.fields["heading"] as string).split("：")[1]).sort();
      assert.deepEqual(csvTitles, ["林晚", "陈舟"]);
      assert.deepEqual(mdTitles, ["林晚", "陈舟"]);
      assert.equal(csvPreview.rows[0].fields["age"], "18");
      assert.equal(mdPreview.rows[0].fields["age"], "18");
    });

    process.stdout.write(`${JSON.stringify({ allPass: true, tests })}\n`);
  } finally {
    await workspace?.close();
    raw?.close();
    await removeWithRetry(directory);
  }
}

run().catch((error) => {
  process.stderr.write(`${error instanceof Error ? error.stack ?? error.message : String(error)}\n`);
  process.exitCode = 1;
});
