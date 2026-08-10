export type {
  LegacyMigrationActivation,
  LegacyMigrationReport,
  LegacyMigrationStatus
} from "../../../src/types/creation";

export interface LegacyMigrationOptions {
  /** 旧数据根目录（app-settings.json / inspirations.json 所在目录）。 */
  dataRoot: string;
  /** 书库目录；缺省为 dataRoot/AppLibrary。 */
  libraryRoot?: string;
  /** 备份所需最小剩余空间（字节），缺省 200MB。 */
  minFreeBytes?: number;
}
