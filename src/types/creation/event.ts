export interface CreationWorkspaceEvent {
  kind: "committed";
  sequence: number;
  projectId: string | null;
  commandType: string;
  changes: Array<{
    entity: string;
    id: string;
    action: "created" | "updated" | "deleted" | "restored" | "moved";
    revision: number;
  }>;
}

export type CreationProjectListener = (event: CreationWorkspaceEvent) => void;