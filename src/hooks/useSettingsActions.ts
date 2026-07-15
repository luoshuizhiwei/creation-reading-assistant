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

  const loadSettings = useCallback(async () => {
    setLoading(true);
    try {
      const settings = await getSettings();
      applySettings(settings);
      return settings;
    } catch (error) {
      const message = messageFromError(error);
      setSettingsError(message);
      setError(message);
      return undefined;
    } finally {
      setLoading(false);
    }
  }, [applySettings, setError, setLoading, setSettingsError]);

  const patchSettings = useCallback(
    async (patch: AppSettingsPatch) => {
      setLoading(true);
      try {
        const settings = await updateSettings(patch);
        applySettings(settings);
        return settings;
      } catch (error) {
        const message = messageFromError(error);
        setSettingsError(message);
        setError(message);
        return undefined;
      } finally {
        setLoading(false);
      }
    },
    [applySettings, setError, setLoading, setSettingsError]
  );

  const resetSection = useCallback(
    async (section: SettingsSection) => {
      setLoading(true);
      try {
        const settings = await resetSettingsSection(section);
        applySettings(settings);
        return settings;
      } catch (error) {
        const message = messageFromError(error);
        setSettingsError(message);
        setError(message);
        return undefined;
      } finally {
        setLoading(false);
      }
    },
    [applySettings, setError, setLoading, setSettingsError]
  );

  return { applySettings, loadSettings, patchSettings, resetSection };
}

