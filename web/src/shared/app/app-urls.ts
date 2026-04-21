/**
 * App-wide external URLs. Mirrors the desktop `AppUrls` interface.
 * Desktop reads these from `app-urls.properties`; the extension keeps
 * them as constants since it has no classpath resources.
 */
export const AppUrls = {
  homeUrl: "https://crosspaste.com",
  changeLogUrl: "https://github.com/crosspaste/crosspaste-desktop/blob/main/CHANGELOG.md",
  issueTrackerUrl: "https://github.com/CrossPaste/crosspaste-desktop/issues",
} as const;
