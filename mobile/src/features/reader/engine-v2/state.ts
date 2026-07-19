import type { ReaderState } from "./types";

export const idleReaderEngineState: ReaderState = { status: "idle" };

export function isReaderTaskCurrent(state: ReaderState, taskId: string): boolean {
  return (state.status === "loading" || state.status === "ready") && state.taskId === taskId;
}

export function readerStateCanPersist(state: ReaderState): state is Extract<ReaderState, { status: "ready" }> {
  return state.status === "ready";
}
