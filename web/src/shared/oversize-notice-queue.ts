export interface OversizeNoticeMessage {
  type: "OVERSIZE_NOTICE";
  title: string;
  message: string;
}

const STORAGE_KEY = "pending_oversize_notices";
const MAX_QUEUED = 20;

export async function enqueueOversizeNotice(
  notice: OversizeNoticeMessage,
): Promise<void> {
  const store = await chrome.storage.session.get(STORAGE_KEY);
  const pending = (store[STORAGE_KEY] as OversizeNoticeMessage[] | undefined) ?? [];
  pending.push(notice);
  const trimmed =
    pending.length > MAX_QUEUED ? pending.slice(-MAX_QUEUED) : pending;
  await chrome.storage.session.set({ [STORAGE_KEY]: trimmed });
}

export async function drainOversizeNotices(): Promise<OversizeNoticeMessage[]> {
  const store = await chrome.storage.session.get(STORAGE_KEY);
  const pending = (store[STORAGE_KEY] as OversizeNoticeMessage[] | undefined) ?? [];
  if (pending.length === 0) return [];
  await chrome.storage.session.remove(STORAGE_KEY);
  return pending;
}
