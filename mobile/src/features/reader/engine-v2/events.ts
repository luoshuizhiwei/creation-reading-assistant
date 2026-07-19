import type { ReaderEngineEvent, ReaderEngineEventPayload, ReaderEngineListener } from "./types";

export class ReaderEngineEvents {
  private readonly listeners = new Map<ReaderEngineEvent, Set<(payload: unknown) => void>>();

  on<T extends ReaderEngineEvent>(event: T, listener: ReaderEngineListener<T>): () => void {
    const bucket = this.listeners.get(event) ?? new Set<(payload: unknown) => void>();
    bucket.add(listener as (payload: unknown) => void);
    this.listeners.set(event, bucket);
    return () => {
      bucket.delete(listener as (payload: unknown) => void);
      if (!bucket.size) this.listeners.delete(event);
    };
  }

  emit<T extends ReaderEngineEvent>(event: T, payload: ReaderEngineEventPayload[T]): void {
    for (const listener of this.listeners.get(event) ?? []) listener(payload);
  }

  clear(): void {
    this.listeners.clear();
  }
}
