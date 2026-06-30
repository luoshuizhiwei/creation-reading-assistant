import { useCallback } from "react";
import { getSettings, resetSettingsSection, updateSettings } from "@/services/settings-service";
import { useLibraryStore } from "@/stores/library-store";
import { useSettingsStore } from "@/stores/settings-store";
import { useAppStore } from "@/stores/app-store";
import type { AppSettingsPatch, SettingsSection } from "@/types/settings";

function messageFromError(error: unknown): string {
  return error instanceof Error ? error.message : String(error);
}

export function useSettingsActions() {
  const setSettings = useSettingsStore((state) => state.setSettings);
  const setLoading = useSettingsStore((state) => state.setLoading);
  const setReaderSettings = useLibraryStore((state) => state.setReaderSettings);
  const setError = useAppStore((state) => state.setError);

  const applySettings = useCallback(
    (settings: Awaited<ReturnType<typeof getSettings>>) => {
      setSettings(settings);
      setReaderSettings(settings.reader);
    },
    [setReaderSettings, setSettings]
  );

  const loadSettings = useCallback(async () => {
    setLoading(true);
    try {
      applySettings(await getSettings());
    } catch (error) {
      setError(messageFromError(error));
    } finally {
      setLoading(false);
    }
  }, [applySettings, setError, setLoading]);

  const patchSettings = useCallback(
    async (patch: AppSettingsPatch) => {
      setLoading(true);
      try {
        const settings = await updateSettings(patch);
        applySettings(settings);
        return settings;
      } catch (error) {
        setError(messageFromError(error));
        return undefined;
      } finally {
        setLoading(false);
      }
    },
    [applySettings, setError, setLoading]
  );

  const resetSection = useCallback(
    async (section: SettingsSection) => {
      setLoading(true);
      try {
        const settings = await resetSettingsSection(section);
        applySettings(settings);
      } catch (error) {
        setError(messageFromError(error));
      } finally {
        setLoading(false);
      }
    },
    [applySettings, setError, setLoading]
  );

  return { loadSettings, patchSettings, resetSection };
}

