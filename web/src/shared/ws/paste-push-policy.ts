/**
 * A paste_push whose hash we already ingested within this window is treated as
 * an echo of our own push bouncing off a peer (see #4619), not new user
 * content: it is still stored, but never written back to the system clipboard,
 * which is the edge that closes the feedback loop.
 */
export const ECHO_SUPPRESS_MS = 10_000;

/**
 * Decide whether a received paste_push should be written to the system
 * clipboard. Pure so the loop-breaking rules are unit-testable.
 */
export function shouldWriteRemotePaste(params: {
  hash: string;
  lastHash: string;
  priorReceivedAt: number | null;
  now: number;
}): boolean {
  // Clipboard already holds this exact content per our own tracking.
  if (params.hash === params.lastHash) return false;
  // Recently ingested same hash: this push is an echo, not a new copy event.
  if (params.priorReceivedAt !== null && params.now - params.priorReceivedAt < ECHO_SUPPRESS_MS) {
    return false;
  }
  return true;
}
