import { useWebDavSettings } from "./hooks/useWebDavSettings";
import { useLibraryManagers } from "./hooks/useLibraryManagers";
import { useMobileAISettings } from "./hooks/useMobileAISettings";
import { useUpdateCheck } from "./hooks/useUpdateCheck";
import { ProfileHome } from "./ProfileHome";
import { SyncPage } from "./pages/SyncPage";
import { WebDavPage } from "./pages/WebDavPage";
import { LibraryManagersPage } from "./pages/LibraryManagersPage";
import { ReadingAndNotesPage } from "./pages/ReadingAndNotesPage";
import { AISettingsPage } from "./pages/AISettingsPage";
import { AppearancePage } from "./pages/AppearancePage";
import { AboutAndPrivacyPage } from "./pages/AboutAndPrivacyPage";
import { ReaderSettingsPage } from "./pages/ReaderSettingsPage";
import { DiagnosticsPage } from "./pages/DiagnosticsPage";
import type { MobileAppTheme } from "../../utils/mobile-helpers";
import type { MobileBook, MobileSnapshot } from "../../types/mobile";
import type { SyncResultDetail } from "../../hooks/useMobileSync";

export type ProfileSubPage =
  | "sync"
  | "webdav"
  | "tags"
  | "categories"
  | "shelves"
  | "reading"
  | "notes"
  | "ai"
  | "appearance"
  | "storage"
  | "privacy"
  | "about"
  | "reader"
  | "diagnostics";

export function ProfilePage({
  snapshot,
  pairingText,
  paired,
  onPairingTextChange,
  onConnectLan,
  onScanQr,
  onSyncDesktop,
  pendingDownloadCount,
  syncLogs,
  lastSyncResult,
  syncing,
  onRetryFailedUploads,
  onSnapshotChange,
  onMessage,
  onConfirm,
  activePage,
  onSetActivePage,
  appTheme,
  onAppThemeChange,
  onOpenBook,
  focusedNoteId,
  readerSettings,
  onReaderSettingsChange
}: {
  snapshot: MobileSnapshot;
  pairingText: string;
  paired: boolean;
  onPairingTextChange: (value: string) => void;
  onConnectLan: () => void;
  onScanQr: () => void;
  onSyncDesktop: () => void;
  pendingDownloadCount: number;
  syncLogs: string[];
  lastSyncResult?: SyncResultDetail;
  syncing?: boolean;
  onRetryFailedUploads?: () => void;
  onSnapshotChange: (snapshot: MobileSnapshot) => void;
  onMessage: (value: string) => void;
  onConfirm: (dialog: { title: string; message: string; onConfirm: () => void } | null) => void;
  activePage: ProfileSubPage | undefined;
  onSetActivePage: (page: ProfileSubPage | undefined) => void;
  appTheme: MobileAppTheme;
  onAppThemeChange: (theme: MobileAppTheme) => void;
  onOpenBook: (book: MobileBook) => void;
  focusedNoteId?: string;
  readerSettings: import("../../types/mobile").MobileReaderSettings;
  onReaderSettingsChange: (settings: import("../../types/mobile").MobileReaderSettings) => void;
}) {
  const webdav = useWebDavSettings({ snapshot, onSnapshotChange, onMessage, activePage });
  const library = useLibraryManagers({ snapshot, onSnapshotChange, onMessage, onConfirm });
  const ai = useMobileAISettings({ onMessage });
  const update = useUpdateCheck({ onMessage });

  if (activePage === "sync") {
    return (
      <SyncPage
        pairingText={pairingText}
        paired={paired}
        onPairingTextChange={onPairingTextChange}
        onConnectLan={onConnectLan}
        onScanQr={onScanQr}
        onSyncDesktop={onSyncDesktop}
        pendingDownloadCount={pendingDownloadCount}
        syncLogs={syncLogs}
        lastSyncResult={lastSyncResult}
        syncing={syncing}
        onRetryFailedUploads={onRetryFailedUploads}
        onSetActivePage={onSetActivePage}
      />
    );
  }

  if (activePage === "webdav") {
    return <WebDavPage webdav={webdav} onSetActivePage={onSetActivePage} />;
  }

  if (activePage === "tags" || activePage === "categories" || activePage === "shelves") {
    return (
      <LibraryManagersPage
        activePage={activePage}
        snapshot={snapshot}
        library={library}
        onOpenBook={onOpenBook}
        onSetActivePage={onSetActivePage}
      />
    );
  }

  if (activePage === "reading" || activePage === "notes") {
    return (
      <ReadingAndNotesPage
        activePage={activePage}
        snapshot={snapshot}
        onSnapshotChange={onSnapshotChange}
        onMessage={onMessage}
        onConfirm={onConfirm}
        onOpenBook={onOpenBook}
        focusedNoteId={focusedNoteId}
        onSetActivePage={onSetActivePage}
      />
    );
  }

  if (activePage === "ai") {
    return <AISettingsPage ai={ai} onSetActivePage={onSetActivePage} />;
  }

  if (activePage === "appearance") {
    return (
      <AppearancePage
        appTheme={appTheme}
        onAppThemeChange={onAppThemeChange}
        onSetActivePage={onSetActivePage}
      />
    );
  }

  if (activePage === "reader") {
    return (
      <ReaderSettingsPage
        settings={readerSettings}
        onSettingsChange={onReaderSettingsChange}
        onSetActivePage={onSetActivePage}
        onConfirm={onConfirm}
      />
    );
  }

  if (activePage === "diagnostics") {
    return <DiagnosticsPage onSetActivePage={onSetActivePage} onMessage={onMessage} />;
  }

  if (activePage === "storage" || activePage === "privacy" || activePage === "about") {
    return (
      <AboutAndPrivacyPage
        activePage={activePage}
        snapshot={snapshot}
        onSnapshotChange={onSnapshotChange}
        onMessage={onMessage}
        update={update}
        onSetActivePage={onSetActivePage}
        onConfirm={onConfirm}
      />
    );
  }

  return (
    <ProfileHome
      snapshot={snapshot}
      paired={paired}
      appTheme={appTheme}
      webdav={webdav}
      ai={ai}
      update={update}
      onSetActivePage={onSetActivePage}
      onConfirm={onConfirm}
      onMessage={onMessage}
    />
  );
}
