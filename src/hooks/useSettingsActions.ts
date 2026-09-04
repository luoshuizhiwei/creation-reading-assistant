import { useCallback } from "react";
import { getSettings, resetSettingsSection, updateSettings } from "@/services/settings-service";
import { useLibraryStore } from "@/stores/library-store";
import { useSettingsStore } from "@/stores/settings-store";
import { useAppStore } from "@/stores/app-store";
import { executeAction } from "@/utils/async-action";
import type { AppSettingsPatch, SettingsSection } from "@/types/settings";

export function useSettingsActions() {
  const setSettings = useSettingsStore((state) => state.setSettings);
  const setLoading = useSettingsStore((state) => state.setLoading);
  const setSettingsError = useSettingsStore((state) => state.setError);
  const setReaderSettings = useLibraryStore((state) => state.setReaderSettings);
  const setError = useAppStore((state) => state.setError);

  const applySettings = useCallback(
    (settings: Awaited<ReturnType<typeof getSettings>>) => {
      setSettings(settings);
      setSettingsError(undefined);
      setReaderSettings(settings.reader);
    },
    [setReaderSettings, setSettings, setSettingsError]
  );

  const loadSettings = useCallback(async (options?: { force?: boolean }) => {
    if (!options?.force && useSettingsStore.getState().settings) {
      return useSettingsStore.getState().settings;
    }
    return await executeAction(() => getSettings(), {
      setLoading,
      setError: (msg) => { setSettingsError(msg); setError(msg); },
      onSuccess: applySettings
    });
  }, [applySettings, setError, setLoading, setSettingsError]);

  const patchSettings = useCallback(
    async (patch: AppSettingsPatch) => {
      return await executeAction(() => updateSettings(patch), {
        setLoading,
        setError: (msg) => { setSettingsError(msg); setError(msg); },
        onSuccess: applySettings
      });
    },
    [applySettings, setError, setLoading, setSettingsError]
  );

  const resetSection = useCallback(
    async (section: SettingsSection) => {
      return await executeAction(() => resetSettingsSection(section), {
        setLoading,
        setError: (msg) => { setSettingsError(msg); setError(msg); },
        onSuccess: applySettings
      });
    },
    [applySettings, setError, setLoading, setSettingsError]
  );

  return { applySettings, loadSettings, patchSettings, resetSection };
}