import { writeFile } from "node:fs/promises";
import { openCreationWorkspace } from "../electron/main/creation-workspace";
import { seedV9Baseline } from "../electron/main/creation-workspace/v9-baseline-fixture";

async function main(): Promise<void> {
  const workspaceDirectory = process.argv[2];
  const outputFile = process.argv[3];
  if (!workspaceDirectory || !outputFile) {
    throw new Error("Usage: packaged-v9-seed <workspace-directory> <baseline-json>");
  }

  const workspace = await openCreationWorkspace({ directory: workspaceDirectory, testOnlyTargetSchemaVersion: 9 });
  try {
    const baseline = await seedV9Baseline(workspace, { workspaceDirectory });
    await writeFile(outputFile, `${JSON.stringify(baseline, null, 2)}\n`, "utf8");
  } finally {
    await workspace.close();
  }
}

void main().catch((error) => {
  console.error(error instanceof Error ? error.stack ?? error.message : String(error));
  process.exitCode = 1;
});
