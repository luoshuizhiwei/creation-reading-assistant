import { getDesktopApi } from "@/services/ipc-client";
import type {
  CardCommand,
  CardRelation,
  CardSummary,
  CardType,
  CardsListQuery,
  CreateProjectInput,
  CreationProjectNavigation,
  CreationProjectOutline,
  CreationProjectListener,
  CreationProjectSummary,
  CreationStructureResult,
  RelationType,
  SceneBodyView,
  SceneSaveResponse,
  StructureCommand,
  UpdateSceneBodyInput
} from "@/types/creation";

function getCreationApi() {
  const api = getDesktopApi().creation;
  if (!api) {
    throw new Error("创作项目数据接口尚未就绪。");
  }
  return api;
}

export async function listProjects(): Promise<CreationProjectSummary[]> {
  return getCreationApi().listProjects();
}

export async function readProjectNavigation(projectId: string): Promise<CreationProjectNavigation | null> {
  return getCreationApi().readProjectNavigation(projectId);
}

export async function readProjectOutline(projectId: string): Promise<CreationProjectOutline | null> {
  return getCreationApi().readProjectOutline(projectId);
}

export async function createProject(input: CreateProjectInput): Promise<CreationProjectNavigation> {
  return getCreationApi().createProject(input);
}

export async function runStructure(command: StructureCommand | CardCommand): Promise<CreationStructureResult> {
  return getCreationApi().runStructure(command);
}

export async function cardsList(query: CardsListQuery): Promise<CardSummary[]> {
  return getCreationApi().cardsList(query);
}

export async function cardRead(cardId: string): Promise<CardSummary | null> {
  return getCreationApi().cardRead(cardId);
}

export async function cardTypesList(projectId: string): Promise<CardType[]> {
  return getCreationApi().cardTypesList(projectId);
}

export async function relationTypesList(projectId: string): Promise<RelationType[]> {
  return getCreationApi().relationTypesList(projectId);
}

export async function cardRelations(
  cardId: string
): Promise<{ outgoing: CardRelation[]; incoming: CardRelation[] }> {
  return getCreationApi().cardRelations(cardId);
}

export async function readSceneBody(sceneId: string): Promise<SceneBodyView | null> {
  return getCreationApi().readSceneBody(sceneId);
}

export async function updateSceneBody(input: UpdateSceneBodyInput): Promise<SceneSaveResponse> {
  return getCreationApi().updateSceneBody(input);
}

export async function watchProject(
  projectId: string,
  onEvent: CreationProjectListener
): Promise<() => void> {
  return getCreationApi().watchProject(projectId, onEvent);
}
