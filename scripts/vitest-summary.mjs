/**
 * Vitest 结果摘要与 ABI 检测的纯函数模块。
 *
 * 由 scripts/beta-check.mjs 使用；逻辑提取为纯函数以便单元测试，
 * 保证「JSON 正常 / 有失败 / 有 skip / 文件缺失 / 解析失败」都不会假绿。
 *
 * 关键原则：无效或缺失字段绝不通过默认 0 变成全绿。
 */
import { createRequire } from "node:module";

const requireFromHere = createRequire(import.meta.url);

const REQUIRED_NUMBER_FIELDS = [
  "numTotalTestSuites",
  "numPassedTestSuites",
  "numFailedTestSuites",
  "numPendingTestSuites",
  "numTotalTests",
  "numPassedTests",
  "numFailedTests",
  "numPendingTests",
  "numTodoTests"
];

function isNonNegativeInteger(value) {
  return typeof value === "number" && Number.isInteger(value) && value >= 0;
}

/**
 * 校验 Vitest JSON 的结构与数值一致性。
 * 任一条件不满足即返回 { ok: false, reasons }。
 */
export function validateVitestJson(json) {
  const reasons = [];
  if (typeof json !== "object" || json === null || Array.isArray(json)) {
    return { ok: false, reasons: ["JSON 不是对象"] };
  }
  for (const field of REQUIRED_NUMBER_FIELDS) {
    if (!(field in json)) {
      reasons.push(`缺少字段 ${field}`);
    } else if (!isNonNegativeInteger(json[field])) {
      reasons.push(`字段 ${field} 不是非负整数（实际 ${JSON.stringify(json[field])}）`);
    }
  }
  if (reasons.length > 0) return { ok: false, reasons };

  if (json.success !== true) {
    reasons.push(`success !== true（实际 ${JSON.stringify(json.success)}）`);
  }
  if (!(json.numTotalTests > 0)) {
    reasons.push(`numTotalTests 必须 > 0（实际 ${json.numTotalTests}）`);
  }
  const sum =
    json.numPassedTests + json.numFailedTests + json.numPendingTests + json.numTodoTests;
  if (sum !== json.numTotalTests) {
    reasons.push(
      `passed(${json.numPassedTests}) + failed(${json.numFailedTests}) + skipped(${json.numPendingTests}) + todo(${json.numTodoTests}) = ${sum} ≠ total(${json.numTotalTests})`
    );
  }
  const suiteSum =
    json.numPassedTestSuites + json.numFailedTestSuites + json.numPendingTestSuites;
  if (suiteSum !== json.numTotalTestSuites) {
    reasons.push(
      `suite passed(${json.numPassedTestSuites}) + failed(${json.numFailedTestSuites}) + skipped(${json.numPendingTestSuites}) = ${suiteSum} ≠ suite total(${json.numTotalTestSuites})`
    );
  }
  if (json.numFailedTests > 0) reasons.push(`numFailedTests=${json.numFailedTests} > 0`);
  if (json.numFailedTestSuites > 0) reasons.push(`numFailedTestSuites=${json.numFailedTestSuites} > 0`);
  return reasons.length > 0 ? { ok: false, reasons } : { ok: true, reasons };
}

/**
 * 从已校验的 JSON 提取摘要。
 * 仅在 validateVitestJson 返回 ok 后调用。
 */
export function summarizeVitestJson(json) {
  return {
    passedTests: json.numPassedTests,
    failedTests: json.numFailedTests,
    skippedTests: json.numPendingTests,
    todoTests: json.numTodoTests,
    totalTests: json.numTotalTests,
    passedFiles: json.numPassedTestSuites,
    failedFiles: json.numFailedTestSuites,
    skippedFiles: json.numPendingTestSuites,
    success: json.success === true
  };
}

/**
 * 从 testResults 中提取失败测试与 skipped 测试的标题（用于报告，不参与通过计数）。
 */
export function extractTestDetails(json) {
  const failed = [];
  const skipped = [];
  for (const suite of Array.isArray(json.testResults) ? json.testResults : []) {
    for (const assertion of Array.isArray(suite.assertionResults) ? suite.assertionResults : []) {
      const title = `${suite.name ?? ""} > ${assertion.title ?? assertion.fullName ?? ""}`;
      if (assertion.status === "failed") failed.push(title);
      else if (assertion.status === "skipped" || assertion.status === "pending" || assertion.status === "todo") {
        skipped.push(title);
      }
    }
  }
  return { failed, skipped };
}

/**
 * 动态检测运行时 ABI 信息（ESM 安全：createRequire）。
 * 加载 better-sqlite3 后必须实际执行原生绑定：
 *   new Database(":memory:"); db.prepare("select 1").get(); db.close();
 * 以捕获真实的 native binding 错误（例如 ABI 不匹配）。
 * 「require is not defined」这类环境错误不算 ABI 不匹配。
 */
export function detectAbi() {
  const info = {
    nodeVersion: process.version,
    abi: process.versions.modules,
    electronVersion: null,
    nativeLoad: "untested",
    nativeError: null
  };
  try {
    const electronPkg = requireFromHere("electron/package.json");
    info.electronVersion = electronPkg.version ?? null;
  } catch {
    info.electronVersion = null;
  }
  try {
    const Database = requireFromHere("better-sqlite3");
    const db = new Database(":memory:");
    const row = db.prepare("select 1 as one").get();
    if (row?.one !== 1) throw new Error("better-sqlite3 探测查询结果异常");
    db.close();
    info.nativeLoad = "ok";
  } catch (error) {
    const message = error instanceof Error ? error.message : String(error);
    if (/require is not defined|Cannot find module/i.test(message)) {
      info.nativeLoad = "environment-error";
    } else {
      info.nativeLoad = "failed";
    }
    info.nativeError = message;
  }
  return info;
}

/**
 * 对一次 Vitest 运行给出结论。
 *
 * @param {{ summary: object, abi: object, skippedReasons: string[], jsonValid: { ok: boolean, reasons: string[] } }} input
 * @returns {{ ok: boolean, notes: string[] }}
 *   ok=false 表示 Beta 必须失败；notes 为给人看的说明。
 */
export function evaluateVitestRun({ summary, abi, skippedReasons, jsonValid }) {
  const notes = [];
  if (!jsonValid.ok) {
    notes.push(`Reporter 数据无效：${jsonValid.reasons.join("；")}`);
    return { ok: false, notes };
  }
  if (summary.failedTests > 0 || summary.failedFiles > 0) {
    notes.push(`Vitest 有 ${summary.failedTests} 个失败测试 / ${summary.failedFiles} 个失败文件。`);
    return { ok: false, notes };
  }
  notes.push(`Test Files: ${summary.failedFiles} failed | ${summary.passedFiles} passed | ${summary.skippedFiles} skipped`);
  notes.push(`Tests:      ${summary.failedTests} failed | ${summary.passedTests} passed | ${summary.skippedTests} skipped`);
  if (summary.skippedTests > 0) {
    const reasons = skippedReasons.length > 0 ? `（原因：${skippedReasons.join("；")}）` : "";
    notes.push(`测试跳过 ${summary.skippedTests} 个${reasons}`);
  } else if (abi.nativeLoad === "ok") {
    notes.push("测试全部通过；ABI 匹配，无 native 测试被跳过。");
  } else if (abi.nativeLoad === "failed") {
    notes.push(
      `测试全部通过；native 模块不可加载（ABI 不匹配：Node ${abi.nodeVersion} / ABI ${abi.abi}，better-sqlite3 加载失败：${abi.nativeError}）`
    );
  } else if (abi.nativeLoad === "environment-error") {
    notes.push(`测试全部通过；native 探测环境异常（${abi.nativeError}），不计为 ABI 不匹配。`);
  }
  notes.push(`ABI: Node ${abi.nodeVersion} / modules ${abi.abi} / Electron ${abi.electronVersion ?? "unknown"} / better-sqlite3 ${abi.nativeLoad}`);
  return { ok: true, notes };
}
