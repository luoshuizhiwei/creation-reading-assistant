import { ReaderEngineError, type ReaderEngine, type ReaderEngineVersion, type ReaderFormat } from "./types";

export type ReaderEngineProvider = () => ReaderEngine;

export interface ReaderEngineCandidate {
  version: Exclude<ReaderEngineVersion, "auto">;
  engine: ReaderEngine;
}

export class ReaderEngineFactory {
  constructor(
    private readonly legacy: Record<ReaderFormat, ReaderEngineProvider>,
    private readonly v2: Partial<Record<ReaderFormat, ReaderEngineProvider>> = {}
  ) {}

  candidates(format: ReaderFormat, version: ReaderEngineVersion): ReaderEngineCandidate[] {
    const legacyProvider = this.legacy[format];
    const v2Provider = this.v2[format];
    if (version === "legacy") return [{ version: "legacy", engine: legacyProvider() }];
    if (version === "v2") {
      if (!v2Provider) {
        throw new ReaderEngineError("unsupported-format", `V2 暂不支持 ${format}。`, { retryable: false });
      }
      return [{ version: "v2", engine: v2Provider() }];
    }
    const candidates: ReaderEngineCandidate[] = [];
    if (v2Provider) candidates.push({ version: "v2", engine: v2Provider() });
    candidates.push({ version: "legacy", engine: legacyProvider() });
    return candidates;
  }
}
