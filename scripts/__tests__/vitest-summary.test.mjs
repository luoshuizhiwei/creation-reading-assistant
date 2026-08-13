import { describe, expect, it } from "vitest";
import {
  detectAbi,
  evaluateVitestRun,
  extractTestDetails,
  summarizeVitestJson,
  validateVitestJson
} from "../vitest-summary.mjs";

const healthyJson = {
  numTotalTestSuites: 3,
  numPassedTestSuites: 3,
  numFailedTestSuites: 0,
  numPendingTestSuites: 0,
  numTotalTests: 40,
  numPassedTests: 40,
  numFailedTests: 0,
  numPendingTests: 0,
  numTodoTests: 0,
  success: true,
  testResults: [
    { name: "suite-a", assertionResults: [{ title: "t1", status: "passed" }, { title: "t2", status: "passed" }] }
  ]
};

const failedJson = {
  ...healthyJson,
  numFailedTests: 2,
  numFailedTestSuites: 1,
  numPassedTests: 38,
  success: false,
  testResults: [
    {
      name: "suite-a",
      assertionResults: [
        { title: "ok", status: "passed" },
        { title: "bad", status: "failed", fullName: "suite-a bad" }
      ]
    }
  ]
};

const skippedJson = {
  ...healthyJson,
  numPendingTests: 4,
  numPassedTests: 36,
  numTotalTests: 40,
  testResults: [
    {
      name: "suite-a",
      assertionResults: [
        { title: "native-legacy-a", status: "skipped" },
        { title: "native-legacy-b", status: "pending" },
        { title: "ok", status: "passed" }
      ]
    }
  ]
};

const baseAbi = { nativeLoad: "ok", nodeVersion: "v24", abi: "137", electronVersion: "33" };

describe("vitest-summary 纯函数", () => {
  it("正常 JSON：校验通过、计数正确、skipped 不计入 passed", () => {
    const validation = validateVitestJson(healthyJson);
    expect(validation.ok).toBe(true);
    const summary = summarizeVitestJson(healthyJson);
    expect(summary.passedTests).toBe(40);
    expect(summary.failedTests).toBe(0);
    expect(summary.skippedTests).toBe(0);
    expect(summary.success).toBe(true);
  });

  it("有失败：failedTests > 0 时结论必须失败", () => {
    const validation = validateVitestJson(failedJson);
    expect(validation.ok).toBe(false);
    const verdict = evaluateVitestRun({
      summary: summarizeVitestJson(failedJson),
      abi: baseAbi,
      skippedReasons: [],
      jsonValid: validation
    });
    expect(verdict.ok).toBe(false);
  });

  it("{} 空对象：必须无效", () => {
    const validation = validateVitestJson({});
    expect(validation.ok).toBe(false);
    const verdict = evaluateVitestRun({
      summary: summarizeVitestJson({}),
      abi: baseAbi,
      skippedReasons: [],
      jsonValid: validation
    });
    expect(verdict.ok).toBe(false);
    expect(verdict.notes.some((note) => note.includes("Reporter 数据无效"))).toBe(true);
  });

  it("success !== true：必须无效", () => {
    const json = { ...healthyJson, success: false, numFailedTests: 0 };
    const validation = validateVitestJson(json);
    expect(validation.ok).toBe(false);
  });

  it("totalTests <= 0：必须无效", () => {
    const json = { ...healthyJson, numTotalTests: 0, numPassedTests: 0 };
    const validation = validateVitestJson(json);
    expect(validation.ok).toBe(false);
  });

  it("passed + failed + skipped + todo 与 total 不一致：必须无效", () => {
    const json = { ...healthyJson, numPassedTests: 39, numTotalTests: 40 };
    const validation = validateVitestJson(json);
    expect(validation.ok).toBe(false);
  });

  it("suite 数量字段缺失：必须无效", () => {
    const json = { ...healthyJson };
    delete json.numTotalTestSuites;
    const validation = validateVitestJson(json);
    expect(validation.ok).toBe(false);
  });

  it("数字字段类型错误（字符串）：必须无效，不用默认 0 假绿", () => {
    const json = { ...healthyJson, numPassedTests: "40" };
    const validation = validateVitestJson(json);
    expect(validation.ok).toBe(false);
  });

  it("非对象（null/数组/字符串）：必须无效", () => {
    expect(validateVitestJson(null).ok).toBe(false);
    expect(validateVitestJson([]).ok).toBe(false);
    expect(validateVitestJson("not json").ok).toBe(false);
  });

  it("JSON 文件截断（解析失败由调用方处理）：解析异常数据不得被 summarize 默认 0 掩盖", () => {
    // 模拟截断：字段缺失（如 numFailedTests 缺失）→ 校验失败而非默认 0 全绿
    const truncated = { ...healthyJson };
    delete truncated.numFailedTests;
    expect(validateVitestJson(truncated).ok).toBe(false);
  });

  it("有 skip：skipped 从结果动态读取并附原因，不警告为错误", () => {
    const validation = validateVitestJson(skippedJson);
    expect(validation.ok).toBe(true);
    const summary = summarizeVitestJson(skippedJson);
    expect(summary.skippedTests).toBe(4);
    const details = extractTestDetails(skippedJson);
    expect(details.skipped.length).toBe(2);
    const verdict = evaluateVitestRun({
      summary,
      abi: { ...baseAbi, nativeLoad: "failed", nativeError: "NODE_MODULE_VERSION mismatch" },
      skippedReasons: ["better-sqlite3 ABI 不匹配（NODE_MODULE_VERSION mismatch）"],
      jsonValid: validation
    });
    expect(verdict.ok).toBe(true);
    expect(verdict.notes.some((note) => note.includes("测试跳过 4 个"))).toBe(true);
  });

  it("ABI 匹配且无 skip 时不得发出错误警告", () => {
    const validation = validateVitestJson(healthyJson);
    const verdict = evaluateVitestRun({
      summary: summarizeVitestJson(healthyJson),
      abi: baseAbi,
      skippedReasons: [],
      jsonValid: validation
    });
    expect(verdict.ok).toBe(true);
    expect(verdict.notes.some((note) => note.startsWith("测试跳过"))).toBe(false);
    expect(verdict.notes.some((note) => note.includes("ABI 匹配"))).toBe(true);
  });

  it("失败测试标题可从 testResults 提取", () => {
    const details = extractTestDetails(failedJson);
    expect(details.failed.length).toBe(1);
    expect(details.failed[0]).toContain("bad");
  });

  it("detectAbi 返回运行时真实信息并实际执行 native binding（不抛错）", () => {
    const abi = detectAbi();
    expect(typeof abi.nodeVersion).toBe("string");
    expect(typeof abi.abi).toBe("string");
    expect(["ok", "failed", "environment-error"]).toContain(abi.nativeLoad);
    if (abi.nativeLoad === "failed") {
      expect(abi.nativeError).toBeTruthy();
      expect(abi.nativeError).not.toContain("require is not defined");
    }
  });
});
