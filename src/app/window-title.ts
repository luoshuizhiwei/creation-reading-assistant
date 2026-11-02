import { useEffect } from "react";

/**
 * BD-227（WCAG 2.4.2 页面标题，Level A）：整窗的标题此前恒等于「创作阅读助手」——
 * 17 屏实测（/tmp/bd227-baseline.log）document.title 与窗口标题都只有这一种，
 * 而这一屏在做的事（写作台、大纲、收件箱、书库……）只写在页面里面。
 * 后果是：读屏器的窗口清单、系统的任务栏/窗口切换器（Alt+Tab）、多窗口对账时
 * 全部拿不到「这个窗口现在在干什么」；专注模式把侧栏与标题栏都藏起来之后更明显。
 *
 * 口径：
 *  · 页面里的 <h1> 用的那份文案是唯一的正身来源（screenTitles / PROJECT_NAV_ITEMS），
 *    这里只是把它抄进窗口标题，不再新造一份叫法——两份标题迟早会漂移；
 *  · owner 机制：外层（壳）发布「这一页是什么」，内层（工作台）在壳之上细化成
 *    「这个项目 · 这个视图」。谁最后发布谁就是当前任务；内层卸载或改页时撤回自己，
 *    上一层的叫法自动回来，不需要谁去「恢复默认」。
 */
const APP_NAME = "创作阅读助手";
const subjects = new Map<string, string>();

/** 把 owner 那份主题写进窗口标题；subject 为 null 表示该 owner 撤回。 */
export function publishWindowTitle(owner: string, subject: string | null): void {
  if (subject) {
    subjects.delete(owner);
    subjects.set(owner, subject);
  } else {
    subjects.delete(owner);
  }
  const top = [...subjects.values()].pop();
  document.title = top ? `${top} — ${APP_NAME}` : APP_NAME;
}

/**
 * 组件用：在挂载期间发布这份主题，卸载或主题变化时先撤回再重发。
 * owner 要在同层里唯一（壳用 "screen"，工作台用 "workbench"）。
 */
export function useWindowTitle(owner: string, subject: string | null): void {
  useEffect(() => {
    publishWindowTitle(owner, subject);
    return () => publishWindowTitle(owner, null);
  }, [owner, subject]);
}
