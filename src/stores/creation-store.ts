import { create } from "zustand";
import {
  createCardSlice,
  createProjectSlice,
  type CardSlice,
  type ProjectSlice
} from "./creation";

export * from "./creation";

export type CreationState = ProjectSlice & CardSlice;

/**
 * useCreationStore — 创作中心聚合状态仓库
 *
 * 采用 Zustand 切片架构（Slice Pattern），将状态与变更动作按领域拆分为：
 * - ProjectSlice (`project-slice.ts`): 项目列表、选区、导航树、场景正文缓存、离开守卫及深链请求
 * - CardSlice (`card-slice.ts`): 设定卡片、卡片类型、关系类型与卡片关系缓存
 *
 * 保持 100% 向后兼容，消费者既可继续通过 useCreationStore 取用全域状态，也可按需解构。
 */
export const useCreationStore = create<CreationState>((...args) => ({
  ...createProjectSlice(...args),
  ...createCardSlice(...args)
}));
