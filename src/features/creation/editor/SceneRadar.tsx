import { Target } from "lucide-react";
import { Button } from "@/components/ui";
import type { SceneRadarData } from "@/features/creation/editor/scene-radar";
import "@/features/creation/editor/scene-radar.css";

interface SceneRadarProps {
  radar: SceneRadarData;
  onOpenOutline(): void;
}

function RadarItem({ label, value }: { label: string; value: string | null }) {
  return (
    <div className="scene-radar-item">
      <dt>{label}</dt>
      <dd className={value ? "" : "unset"}>{value ?? "—"}</dd>
    </div>
  );
}

/** 场景雷达（调研 D-C1 v1）：任务卡字段 + 字数进度 + 批注/引用概览，纯只读聚合。 */
export function SceneRadar({ radar, onOpenOutline }: SceneRadarProps) {
  const progress =
    radar.targetWords && radar.targetWords > 0
      ? Math.min(100, Math.round((radar.characterCount / radar.targetWords) * 100))
      : null;

  return (
    <div className="scene-radar" data-testid="scene-radar">
      {!radar.hasPlanning && (
        <div className="scene-radar-empty">
          <Target size={16} />
          <p>这个场景还没有任务卡。在大纲页记录视角、时间、地点、目标与冲突，写作时雷达会在这里汇总。</p>
          <Button size="sm" variant="outline" onClick={onOpenOutline}>前往大纲填写</Button>
        </div>
      )}

      {radar.hasPlanning && (
        <>
          <dl className="scene-radar-grid">
            <RadarItem label="视角角色" value={radar.perspective} />
            <RadarItem label="时间" value={radar.planning?.time ?? null} />
            <RadarItem label="地点" value={radar.location} />
            <RadarItem label="目标" value={radar.planning?.goal ?? null} />
            <RadarItem label="冲突" value={radar.planning?.conflict ?? null} />
            <RadarItem label="结果" value={radar.planning?.outcome ?? null} />
            <RadarItem label="情绪" value={radar.planning?.emotion ?? null} />
          </dl>
          {radar.cast.length > 0 && (
            <div className="scene-radar-block">
              <dt>出场角色</dt>
              <dd className="scene-radar-tags">
                {radar.cast.map((title) => (
                  <span key={title} className="scene-radar-tag">{title}</span>
                ))}
              </dd>
            </div>
          )}
        </>
      )}

      <div className="scene-radar-block scene-radar-words">
        <div className="scene-radar-words-row">
          <dt>字数{radar.targetWords ? " / 目标" : ""}</dt>
          <dd>
            {radar.characterCount.toLocaleString("zh-CN")}
            {radar.targetWords ? ` / ${radar.targetWords.toLocaleString("zh-CN")}` : ""}
            {radar.estimatedMinutes ? ` · 约读 ${radar.estimatedMinutes} 分钟` : ""}
          </dd>
        </div>
        {progress !== null && (
          <span
            className="scene-radar-progressbar"
            role="progressbar"
            aria-valuenow={progress}
            aria-valuemin={0}
            aria-valuemax={100}
            aria-label={`目标进度 ${progress}%`}
          >
            <span className="scene-radar-progressbar-fill" style={{ width: `${progress}%` }} />
          </span>
        )}
      </div>

      <div className="scene-radar-block">
        <dt>批注概览</dt>
        <dd className="scene-radar-note">
          待处理 {radar.unresolvedAnnotations} · 已解决 {radar.resolvedAnnotations}
          {radar.invalidAnchors > 0 && <em className="scene-radar-invalid"> · 失效锚点 {radar.invalidAnchors}</em>}
          {" "}· 修订 r{radar.revision}
        </dd>
        {radar.foreshadowOpenInProject > 0 && (
          <dd className="scene-radar-note scene-radar-foreshadow">
            待回收伏笔：本场 {radar.foreshadowOpenInScene} · 全书 {radar.foreshadowOpenInProject}
          </dd>
        )}
        {radar.referencedCards.length > 0 && (
          <dd className="scene-radar-tags">
            {radar.referencedCards.map((ref) => (
              <span
                key={`${ref.kindName}-${ref.title}`}
                className={`scene-radar-tag ${ref.isForeshadow ? (ref.foreshadowResolved ? "foreshadow-resolved" : "foreshadow-open") : ""}`}
                title={ref.isForeshadow ? `伏笔线索 · ${ref.foreshadowResolved ? "已回收" : "未回收"}` : ref.kindName}
              >
                {ref.kindName && <em>{ref.kindName}</em>}
                {ref.title}
                {ref.isForeshadow && <b>{ref.foreshadowResolved ? "已回收" : "未回收"}</b>}
              </span>
            ))}
          </dd>
        )}
      </div>
    </div>
  );
}
