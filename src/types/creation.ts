// src/types/creation.ts
// 本文件作为向后兼容的 Barrel Re-export 入口，保留所有现有 import 路径。
// 细分类型已按领域/职责拆分到 ./creation/ 子目录下。
export * from "./creation/primitives";
export * from "./creation/model";
export * from "./creation/command";
export * from "./creation/query";
export * from "./creation/event";
export * from "./creation/plan";
export * from "./creation/runtime";