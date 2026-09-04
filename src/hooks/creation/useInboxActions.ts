import { useCallback } from "react";
import {
  inboxCount as inboxCountRequest,
  inboxCreate as inboxCreateRequest,
  inboxDelete as inboxDeleteRequest,
  inboxList as inboxListRequest,
  inboxUpdate as inboxUpdateRequest
} from "@/services/creation-service";
import { useAppStore } from "@/stores/app-store";
import type {
  InboxCountView,
  InboxCreateCommand,
  InboxDeleteCommand,
  InboxItem,
  InboxListQuery,
  InboxUpdateCommand
} from "@/types/creation";
import { executeAction, executeBoolAction } from "@/utils/async-action";

export function useInboxActions() {
  const setError = useAppStore((state) => state.setError);

  const loadInbox = useCallback(
    async (query: Omit<InboxListQuery, "kind">): Promise<InboxItem[]> => {
      const res = await executeAction(
        () => inboxListRequest({ kind: "inbox.list", ...query }),
        { setError }
      );
      return res ?? [];
    },
    [setError]
  );

  const loadInboxCount = useCallback(async (): Promise<InboxCountView> => {
    const res = await executeAction(() => inboxCountRequest(), { setError });
    return res ?? { total: 0, pending: 0 };
  }, [setError]);

  const updateInbox = useCallback(
    async (command: Omit<InboxUpdateCommand, "type">): Promise<boolean> => {
      return executeBoolAction(
        () => inboxUpdateRequest({ type: "inbox.update", ...command }),
        { setError }
      );
    },
    [setError]
  );

  const deleteInbox = useCallback(
    async (command: Omit<InboxDeleteCommand, "type">): Promise<boolean> => {
      return executeBoolAction(
        () => inboxDeleteRequest({ type: "inbox.delete", ...command }),
        { setError }
      );
    },
    [setError]
  );

  const createInbox = useCallback(
    async (command: Omit<InboxCreateCommand, "type">): Promise<string | undefined> => {
      const res = await executeAction(
        () => inboxCreateRequest({ type: "inbox.create", ...command }),
        { setError }
      );
      return res?.itemId;
    },
    [setError]
  );

  return {
    loadInbox,
    loadInboxCount,
    updateInbox,
    deleteInbox,
    createInbox
  };
}
