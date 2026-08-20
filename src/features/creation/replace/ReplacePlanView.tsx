import type { ReplaceHit, ReplacePlanSceneSummary, ReplacePlanView } from "./types";

interface ReplacePlanViewProps {
  plan: ReplacePlanView;
  excluded: Set<string>;
  onToggleHit: (hitId: string) => void;
  onToggleScene: (sceneId: string, hitIds: string[], excludeAll: boolean) => void;
}

function SceneGroup({
  scene,
  hits,
  excluded,
  onToggleHit,
  onToggleScene
}: {
  scene: ReplacePlanSceneSummary;
  hits: ReplaceHit[];
  excluded: Set<string>;
  onToggleHit: (hitId: string) => void;
  onToggleScene: (sceneId: string, hitIds: string[], excludeAll: boolean) => void;
}) {
  const excludedCount = hits.filter((hit) => excluded.has(hit.hitId)).length;
  const allExcluded = excludedCount === hits.length;
  const someExcluded = excludedCount > 0 && !allExcluded;
  const hitIds = hits.map((hit) => hit.hitId);
  return (
    <section className="replace-scene-group" data-testid={`scene-group-${scene.sceneId}`}>
      <header className="replace-scene-header">
        <label className="replace-scene-toggle">
          <input
            type="checkbox"
            aria-label={`排除场景 ${scene.title}`}
            checked={allExcluded}
            ref={(el) => {
              if (el) el.indeterminate = someExcluded;
            }}
            onChange={(event) => onToggleScene(scene.sceneId, hitIds, event.target.checked)}
          />
          <span className="replace-scene-title">{scene.title || "未命名场景"}</span>
          <span className="replace-scene-meta">
            {scene.chapterTitle} · 命中 {hits.length} 处
          </span>
        </label>
        {someExcluded && (
          <span className="replace-scene-partial">已排除 {excludedCount} 处</span>
        )}
      </header>
      <ul className="replace-hit-list">
        {hits.map((hit) => {
          const isExcluded = excluded.has(hit.hitId);
          return (
            <li key={hit.hitId} className="replace-hit-row" data-testid={`hit-row-${hit.hitId}`}>
              <label className="replace-hit-toggle">
                <input
                  type="checkbox"
                  aria-label={`排除命中 ${hit.hitId}`}
                  checked={isExcluded}
                  onChange={() => onToggleHit(hit.hitId)}
                />
              </label>
              <div className="replace-hit-diff">
                <span className="replace-hit-before">{hit.before}</span>
                <span className="replace-hit-arrow" aria-hidden="true">
                  →
                </span>
                <span className="replace-hit-after">{hit.after}</span>
              </div>
              <code className="replace-hit-context">{hit.context}</code>
            </li>
          );
        })}
      </ul>
    </section>
  );
}

export function ReplacePlanView({ plan, excluded, onToggleHit, onToggleScene }: ReplacePlanViewProps) {
  const remaining = plan.totalHits - excluded.size;
  return (
    <div className="replace-plan-view" data-testid="replace-plan-view">
      <div className="replace-plan-summary">
        <span data-testid="replace-total-hits">共 {plan.totalHits} 处命中</span>
        {plan.truncated && (
          <span className="replace-plan-truncated" data-testid="replace-truncated">
            （已截断，仅预览前 {plan.limit} 处，请缩小范围或提高上限）
          </span>
        )}
        <span data-testid="replace-remaining">待替换 {remaining} 处</span>
      </div>
      {plan.scenes.map((scene) => (
        <SceneGroup
          key={scene.sceneId}
          scene={scene}
          hits={plan.hits.filter((hit) => hit.sceneId === scene.sceneId)}
          excluded={excluded}
          onToggleHit={onToggleHit}
          onToggleScene={onToggleScene}
        />
      ))}
    </div>
  );
}
