/**
 * Custom MIME type used to mark clipboard content written by CrossPaste.
 * The "web " prefix is required by Chrome for custom MIME types in the
 * Clipboard API. When the poller detects this marker, it skips recording.
 */
export const CROSSPASTE_MARKER_MIME = "web application/x-crosspaste-marker";
