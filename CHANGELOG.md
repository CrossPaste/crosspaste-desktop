# Changelog

All notable changes to this project will be documented in this file.

# [2.0.0] - 2026-04-26
# Highlights 🌟

- 🌍 **Chrome Extension — first-class platform**
  CrossPaste now ships a Chrome extension that participates in the sync mesh as a real platform. It speaks the same WebSocket sync protocol as the desktop, supports image preview, large file download via WS pulling, right-click copy, install CTAs on oversize notices, and pauses itself via Native Messaging when the desktop app is running on the same machine (#4152 #4157 #4163 #4167 #4175 #4223 #4227 #4230 #4286 #4288).

- 🔌 **WebSocket sync protocol**
  A bidirectional WebSocket sync protocol replaces the previous polling/HTTP flow, with batch paste pull endpoints for incremental sync and an active liveness probe. Reconnection uses a fast-then-slow telnet probe strategy for snappier recovery (#4111 #4119 #4107 #4223).

- 🔐 **SAS-based v2 pairing**
  A new Short-Authentication-String pairing protocol prevents MITM attacks during device pairing, with a `showPairingCode` endpoint and remote-access toggle for lower-friction trust establishment (#4088 #4095).

- 🏗️ **Core module + architectural cleanup**
  Extracted a `core` module to deduplicate shared logic between `app` and `shared`, unified coroutine scopes via a `namedScope` helper, refactored `SyncResolver` to a unified single-pass connection flow, namespaced source icons by `appInstanceId`, and converted `withTimeout` overloads to `Duration` (#4060 #4103 #4191 #4215 #4093).

- 🇵🇹 **Portuguese support**
  Added full Portuguese (`pt`) translations across the app (#4076 #4090 #4284 #4299).

# Bug Fixes 🐛

- :bug: Keep `javaFileListFlavor` alongside `imageFlavor` for single images (#4302)
- :bug: Align `PasteCard.canDrag` with `handleDragStart` guards (#4293)
- :bug: Fail fast when `crosspaste-version.properties` is missing the version key (#4277)
- :bug: Serialize oversize-notice drains to prevent duplicate display (#4275)
- :bug: Use `probe()` instead of `isConnected()` for extension reconnect (#4258)
- :bug: Rethrow `CancellationException` in `WsSession.ping` (#4257)
- :bug: Keep re-pair / edit-note dialogs mounted from `DevicesView` detail view (#4259)
- :bug: Handle `DECRYPT_FAIL` inline in `syncAllDevices` (#4251)
- :bug: Always enqueue oversize-paste notices before notifying side panel (#4247)
- :bug: Preserve Chrome extension scroll position on paste delete (#4237)
- :bug: Fix Chrome extension right-click Copy duplicating HTML/image pastes (#4235)
- :bug: Fix Chrome extension download for MV3 SW + enable image download (#4232)
- :bug: Reactively read port in `CurrentDeviceDialog` (#4213)
- :bug: Restore ESC key binding for `hide_window` and surface it in shortcuts UI (#4183)
- :bug: Fix robustness issues in Chrome extension and Native Messaging integration (#4177)
- :bug: Fix Chrome extension sync ordering and write synced clipboard (#4171)
- :bug: Fix Chrome extension image preview for synced clipboard images (#4170)
- :bug: Fix `PasteData` serializer registration on Kotlin/JS and clean up remote flag handling (#4165)
- :bug: Use KMP-compatible `concatToString` in `ConnectCodeUtils` (#4154)
- :bug: Skip sync for remote clipboard data to prevent duplication (#4147)
- :bug: Fix incorrect MCP SSE endpoint URL in configuration command (#4145)
- :bug: Deprioritize virtual network interfaces in auto-selection (#4137)
- :bug: Fix `SegmentedControlSettingsRow` layout in `twoLine` mode (#4135)
- :bug: Verify connection immediately after server-side trust completes (#4109)
- :bug: Fix pairing-code countdown freeze by reordering trust completion steps (#4101)
- :bug: Fix Sparkle crash by disabling redundant scheduled check and ensuring main-thread dispatching (#4085)
- :bug: Fix missing `add_device_manually_desc` key in `zh_hant` (#4078)
- :bug: Fix HTML/RTF search content returning raw markup (#4061)
- :bug: Fix `ToStartView` showing on open and not appearing when scrolled (#4051)
- :bug: Fix missing `libtesseract.so` in Linux builds since 1.2.6 (#4048)
- :bug: Fix flaky crypto tests that assume wrong-key decryption always throws (#4045)
- :bug: Fix color hex format in `MarketingPasteData` from ARGB to RGBA (#4043)
- :bug: Fix image side preview crop fill and blurry decode size (#4041)
- :bug: Fix notification close button intercepted by native title bar buttons on Windows (#4039)

# New Features ✨

- :sparkles: Add Chrome Extension as a first-class platform type (#4157)
- :sparkles: Make sync protocol fully compatible with Chrome Extension (#4163)
- :sparkles: Add WebSocket-based file pulling for Chrome Extension (#4167)
- :sparkles: Add Native Messaging support to pause Chrome extension when desktop app is running (#4175)
- :sparkles: Active WebSocket probe for Chrome Extension liveness (#4223)
- :sparkles: Notify Chrome extension when paste is skipped for size limit (#4228)
- :sparkles: Chrome extension SyncState parity with desktop (#4227)
- :sparkles: Chrome extension UISupport helpers + install CTA on oversize notice (#4230)
- :sparkles: Add native app download banner to extension settings (#4288)
- :sparkles: Allow Native Messaging from Chrome Web Store release extension ID (#4286)
- :sparkles: Add support for image drag from side panel; warn for file drag (#4282)
- :sparkles: Add user-configurable JVM system-property override file (#4267)
- :sparkles: Add WebSocket sync protocol for bidirectional real-time communication (#4111)
- :sparkles: Add batch paste pull endpoint for incremental sync (#4119)
- :sparkles: Add `showPairingCode` endpoint with remote access toggle (#4095)
- :sparkles: Add SAS-based v2 pairing protocol to prevent MITM attacks (#4088)
- :sparkles: Add loading indicator for pasteboard lazy loading and fix throttle stall (#4053)
- :sparkles: Add Portuguese (`pt`) language support (#4076)
- :sparkles: Add `fa`/`pt` translations for extension-only i18n keys (#4299)

# UI Improvements 💄

- :lipstick: Redesign devices screen with offline grouping and current-device dialog (#4206)
- :lipstick: Add mobile app promotion guide in desktop devices view (#4083)
- :lipstick: Improve Portuguese i18n conciseness for sync-related keys (#4090)
- :art: Align add-device button style with current-device button (#4273)
- :art: Shrink `OfflineToggle` switch scale to 0.7× (#4211)

# Multiplatform · Refactor · Code Style 🔨

- :hammer: Stop leaking raw exception messages to notifications (#4291)
- :hammer: Pass `desktopConnected` as prop to `SettingsView` (#4297)
- :hammer: Extract `IMAGE_MIME_MAP` into shared mime utility (#4295)
- :hammer: Switch Chrome extension to `optional_host_permissions` (#4281)
- :hammer: Split `AppSourceIcon` and `SideAppSourceIcon` transformer pipelines (#4271)
- :hammer: Dedupe concurrent `refresh()` calls and support `zh_hant` fallback (#4256)
- :hammer: Extract `useCopyWithNotification` hook (#4249)
- :hammer: Unify Chrome extension version with `crosspaste-version.properties` (#4245)
- :hammer: Polish `WsSessionManager` TOCTOU, ping mutex, `namedScope` (#4224)
- :hammer: Remove P2P Test debug panel from Chrome extension (#4219)
- :hammer: Unify coroutine scopes via `namedScope` helper (#4215)
- :hammer: Extract `core` module and deduplicate `shared`/`app` code (#4060)
- :hammer: Refactor `SyncResolver` to unified single-pass connection flow (#4103)
- :hammer: Namespace source icons by `appInstanceId` (#4191)
- :hammer: Enforce namespaced icon paths in sync endpoints (#4192)
- :hammer: Refactor `WsPullFileRequest` to sealed class, extract plugins, and improve robustness (#4179)
- :hammer: Refactor `addSyncTask` to accept `targetAppInstanceIds` as `Set<String>` (#4181)
- :hammer: Convert `withTimeout`/`withTimeoutOrNull` `Long` overloads to `Duration` (#4093)
- :hammer: Abstract `TrustDeviceView` in `DeviceScope` for platform-specific pairing (#4097)
- :hammer: Replace custom `CountBadgeAuto` with Material 3 `Badge` and add image count badge (#4142)
- :hammer: Refactor `getCrossPasteWebUrl` to use remote locale config from `meta.json` (#4123)
- :hammer: Refactor `searchPasteData.pasteType` to `pasteTypeList` for multi-type filtering (#4121)
- :hammer: Reduce default shortcut key bindings to `show_main` and `show_search` only (#4126)
- :hammer: Migrate `promote.json` to web API and update data model (#4149)
- :hammer: Simplify `DesktopPromoteGuide` to single download button (#4155)
- :hammer: Extract shared `AppSourceIcon` composable (#4196)
- :hammer: Normalize app source icon padding across platforms (#4203)
- :hammer: Remove `getSearchContent`/`getSummary` from `PasteItem`, introduce `PasteItemReader` and `PasteDataHelper` (#4066)
- :heavy_minus_sign: Remove duplicate exception classes from app module (#4099)
- :heavy_minus_sign: Remove unused `material-kolor` dependency (#4081)

# Performance ⚡

- :zap: Improve device reconnection with fast-then-slow telnet probe strategy (#4107)
- :zap: Skip icon pull when local icon file already exists (#4194)

# Build & CI 👷

- :construction_worker: Tighten CI path filters and unify Node version in build-release (#4279)
- :construction_worker: Build Chrome extension zip and upload to OSS on release (#4241)
- :construction_worker: Split CI into app-build and web-build with path filters (#4239)

# Dependencies ⬆️

- ⬆️ **Kotlin** 2.3.10 → 2.3.20 (#4069)
- ⬆️ **Compose Plugin** 1.10.1 → 1.10.3 (#4055 #4068)
- ⬆️ **Ktor** 3.4.0 → 3.4.2 (#4073 #4128)
- ⬆️ **Koin** 4.1.1 → 4.2.1 (#4115 #4159)
- ⬆️ **SQLDelight** 2.2.1 → 2.3.2 (#4114)
- ⬆️ **Conveyor** 1.13 → 2.0 (#4071)
- ⬆️ **Cryptography** 0.5.0 → 0.6.0 (#4132)
- ⬆️ **Coil** 3.3.0 → 3.4.0 (#4210)
- ⬆️ **Lifecycle** 2.9.6 → 2.10.0 (#4070)
- ⬆️ **Logback** 1.5.31 → 1.5.32 (#4134)
- ⬆️ **Okio** 3.16.4 → 3.17.0 (#4133)
- ⬆️ **MCP Kotlin SDK Server** 0.8.4 → 0.9.0+ (#4024 #4113 #4162)
- ⬆️ **Jewel** 0.33.0 → 0.34.0 (#4056 #4129)

---
**Full Changelog**: https://github.com/CrossPaste/crosspaste-desktop/compare/1.2.9.2059...2.0.0.2192

# [1.2.9] - 2026-03-11
# Highlights 🌟

- 🖥️ **Native lazy clipboard writes**
  Files copied to the clipboard are now provided lazily on macOS via `NSPasteboardItemDataProvider` and on Windows via JNA delayed rendering, avoiding upfront materialization of large file payloads (#3929 #3931).

- 🏷️ **Tag-based favorites**
  The legacy boolean "favorite" flag is gone — favorites are now just another tag, opening the door to user-defined organizational tags. New `AppControl` hooks support tag creation and pinning (#3989 #3991).

- 🧱 **Modularization groundwork**
  Initialized `shared-ui` and `web` Gradle modules, moved SQLDelight into the shared module and extracted DAO interfaces, rewrote CLI commands from HTTP to direct DAO and native access, and split `DesktopModule` DI into focused sub-modules (#3954 #3950 #3952 #3937).

- 🎯 **"Sync To" context menu**
  Right-click any paste to send it to a specific device on demand, instead of relying on background sync (#4019).

# Bug Fixes 🐛

- :bug: Fix duplicate JAR version conflicts in MSIX packages (#4026)
- :bug: Fix CLI cross-platform build failures for Windows and cross-compilation (#4030)
- :bug: Fix `SQLITE_BUSY` errors by serializing `SyncRuntimeInfoDao` writes (#3994)
- :bug: Fix `SearchWindow` position flash on macOS after long background (#3984)
- :bug: Fix devices list overlapping FAB and transparent sticky headers (#3982)
- :bug: Refresh storage statistics immediately after cleanup and show progress dialog (#3980)
- :bug: Fix `IllegalStateException` in `compressExportFile` and add export/import tests (#3976)
- :bug: Improve JmDNS service discovery reliability in `refreshAll` and `serviceRemoved` (#3974)
- :bug: Recreate `LazyListState` on window open to reliably reset scroll position (#3962)
- :bug: Fix theme flash from light to dark on startup (#3960)

# New Features ✨

- :sparkles: Add right-click "Sync To" context menu for syncing paste to specific device (#4019)
- :sparkles: Add rounded corners for main window on Windows and Linux (#4006)
- :sparkles: Add adaptive QR code sizing for multi-device compatibility (#4010)
- :sparkles: Add `AppControl` hooks for tag creation and pinning (#3991)
- :sparkles: Auto-close token view when all pending verifiers complete verification (#3972)
- :sparkles: Add headless mode for CLI-only environments (#3863)
- :sparkles: Add Windows native lazy file clipboard write via JNA delayed rendering (#3931)
- :sparkles: Add macOS native lazy file pasteboard write via `NSPasteboardItemDataProvider` (#3929)
- :sparkles: Add `GET /pull/paste` endpoint for iOS pull (#3922)
- :sparkles: Add source exclusion service to filter clipboard by application (#3920)

# Multiplatform · Refactor · Code Style 🔨

- :hammer: Merge `addSyncTask` and `addTargetedSyncTask` into single method (#4021)
- :hammer: Extract `PlatformTokenTextField` expect/actual for iOS backspace support (#4017)
- :hammer: Extract platform-specific number keyboard options for iOS compatibility (#4002)
- :hammer: Redesign `FilledDropdown` component to match prototype design (#4000)
- :hammer: Redesign `Counter` component to match prototype stepper design (#3998)
- :hammer: Redesign desktop About page to match mobile layout (#3996)
- :hammer: Convert favorite boolean to tag-based system (#3989)
- :hammer: Extract `ScreenLayout` into `ScreenProvider` interface with configurable padding (#3967)
- :hammer: Extract `SearchPasteData` and `QueryPasteTag` interfaces from DAO layer (#3956)
- :hammer: Extract `ImageLoaders` into individual DI-registered `ImageLoader` instances (#3958)
- :hammer: Initialize `shared-ui` and `web` Gradle modules (#3954)
- :hammer: Rewrite CLI commands from HTTP to direct DAO and native access (#3952)
- :hammer: Move SQLDelight to shared module, extract DAO interfaces, remove CLI bridge (#3950)
- :hammer: Extract `SyncResolverApi` interface and `SyncDeviceManager` class (#3942)
- :hammer: Split `ColorUtils` into `ColorParser`, `ColorConversion`, and `ColorAccessibility` (#3941)
- :hammer: Extract `DesktopModule` DI sub-modules into `DesktopAppModule`, `DesktopNetworkModule`, `DesktopPasteComponentModule`, `DesktopUiModule` (#3937)
- :hammer: Extract User32 utility functions into focused `WindowsProcessUtils`, `WindowsFocusUtils`, `WindowsIconUtils` (#3935)
- :hammer: Extract `FilePullService` from `PullFileTaskExecutor` (#3933)
- :hammer: Extract `PasteReleaseService` and `PasteTagDao` from `PasteDao` (#3926)
- :hammer: Move `sourceExclusions` to desktop-only config and use `DesktopConfigManager` (#3921)

# Performance ⚡

- :zap: Improve QR code layout for narrow screens (#4012)
- :zap: Async font loading and virtualized font dropdown to eliminate UI stutter (#4004)
- :zap: Use content hash as `LaunchedEffect` key to avoid `equals` on large HTML strings (#3948)
- :zap: Truncate HTML DOM for side preview rendering to prevent UI lag (#3939)

# Build & CI 👷

- :construction_worker: Remove CLI native build from GitHub Actions workflows (#4032)
- :construction_worker: Fix Kotlin Default Hierarchy Template warnings for `cli` and `web` modules (#3987)

# Dependencies ⬆️

- ⬆️ **Gradle Wrapper** 9.3.1 → 9.4.0 (#4023)
- ⬆️ **Tesseract Platform** downgraded to 5.5.1-1.5.12 to fix macOS 15.0 minimum-version conflict (#4034)
- ⬆️ **filekit-dialogs** 0.12.0 → 0.13.0 (#4009)
- ⬆️ **SnakeYAML** 2.5 → 2.6 (#4008)
- ⬆️ **imageio** 3.13.0 → 3.13.1 (#3968)
- ⬆️ **MCP Kotlin SDK Server** updated (#3969)
- ⬆️ **composenativetray-jvm** updated (#3924)

---
**Full Changelog**: https://github.com/CrossPaste/crosspaste-desktop/compare/1.2.8.1999...1.2.9.2059

# [1.2.8] - 2026-02-16
# Highlights 🌟

- 🤖 **MCP server**
  CrossPaste now exposes its clipboard history to AI agents through the Model Context Protocol, with a dedicated settings page that lets you toggle the server and configure its port (#3839 #3841).

- ✏️ **BubbleWindow inline editing**
  A new `BubbleWindow` enables inline text and HTML editing directly from the Search window, with a redesigned `PasteTextEditContentView` featuring a floating toolbar and undo/redo (#3837 #3860 #3862).

- ✂️ **Cut with undo**
  Added cut operation with undo support for file and image paste items (#3871).

- 📂 **Smarter large-file sync**
  Large files are now synced directly into the system download directory, and drag-and-drop file pulls follow the same path. The "In Downloads" indicator hides paste items whose backing files have gone missing (#3833 #3842 #3852).

- 🧪 **Testing & code-review push**
  ~700 new unit tests landed across data, sync, network/security, business logic, control layer, utils, ViewModel, and UI state. Multi-instance device-lifecycle integration tests round it out, alongside dozens of code-review-driven fixes for resource leaks, race conditions, and security issues (#3816 #3818 #3820 #3822 #3824 #3825 #3826 #3827).

# Bug Fixes 🐛

- :bug: Fix encrypted file pull failure, retry duplicate files, and missing cleanup (#3916)
- :bug: Fix sync retry loop on `NOT_MATCH_APP_INSTANCE_ID` and delayed `CONNECTED` status on passive device (#3912)
- :bug: Fix `BubbleWindow` white corners and missing tail on Linux (#3910)
- :bug: Fix export count off-by-one, import failure handling and orphaned DB entries (#3908)
- :bug: Fail fast in `ClientEncryptPlugin` when body type is unsupported for encryption (#3902)
- :bug: Harden paste sync pipeline: channel leak, semaphore timeout, and `getProcess` consistency (#3904)
- :bug: Fix `SecureStore` double-check locking and session lifecycle bugs (#3906)
- :bug: Fix import file path collision and export partial failure handling (#3900)
- :bug: Fix cleanup config mismatch: expiration cleanup guarded by wrong flag (#3895)
- :bug: Fix task executor semaphore starvation and unbuffered queue backpressure (#3893)
- :bug: Fix notification race window and unconditional notify in `SyncRuntimeInfoDao` (#3888)
- :bug: Make `started` flag thread-safe with `atomicfu` in `GeneralSyncManager` (#3886)
- :bug: Ensure `notifyExit` completes before scope cancellation via `CompletableDeferred` (#3884)
- :bug: Add missing `return` after `connectState` change to prevent duplicate events (#3882)
- :bug: Enable per-device concurrent sync event processing (#3880)
- :bug: Fix stale `SyncRuntimeInfo` snapshot in sync event processing (#3879)
- :bug: Move sync info diff check from `combine` to `addDevice` to prevent feedback loop (#3876)
- :bug: Fix sync handler coroutine leak and add immediate reconnection on disconnect (#3875)
- :bug: Fix save button and toolbar selection bugs in `PasteHtmlEditContentView` (#3872)
- :bug: Fix `NotFoundNearByDevices` wifi icon tint color (#3850)
- :bug: Fix always-on-top push-pin icon state (#3848)
- :bug: Fix `InfoItem` value text alignment when wrapping (#3831)
- :bug: Fix unchecked cast, short timeout, hardcoded `User-Agent`, and missing design comments (#3814)
- :bug: Fix `StripedMutex` overflow, hardcoded IV size, regex recompilation, and public logger (#3812)
- :bug: Fix Zip Slip vulnerability, regex recompilation, and redundant `hashCode` (#3810)
- :bug: Fix thread-unsafe `RTFEditorKit`, non-local return in `runCatching`, and `Memoize` cache (#3808)
- :bug: Fix share URL encoding, inconsistent delay, missing clipboard copy, and UI issues (#3806)
- :bug: Fix `runBlocking` UI freeze and redundant `koinInject` in device sync UI (#3804)
- :bug: Fix stale discovery toggle, IO thread state write, and dead code in settings (#3800)
- :bug: Fix export filter UI, redundant variable shadow, and Material 2 import (#3798)
- :bug: Fix notification loop, redundant rendering, resource leak, and Material 2 imports (#3796)
- :bug: Fix thread safety, race conditions, and resource leaks in UI infrastructure (#3794)
- :bug: Fix queries returning deleted paste data and `isValid()` treating deleted state as valid (#3792)
- :bug: Fix path resolution silent errors, migration cleanup, and download race condition (#3790)
- :bug: Fix file dialog timing, `runBlocking` in coroutine, dead state, and thread safety (#3788)
- :bug: Fix app lifecycle resource leaks, naming inconsistency, and thread safety (#3786)
- :bug: Fix shortcut key system: wrong macOS rawCode, dead code, fragile reflection (#3784)
- :bug: Fix Linux native integration resource leaks and pointer bugs (#3782)
- :bug: Fix Windows native resource leaks: DC, COM, HICON, GlobalAlloc, and Process (#3780)
- :bug: Fix use-after-free in Swift async dispatches for window management (#3778)
- :bug: Fix `FileExtFetcher` wrong image path and OCR native resource leak (#3776)
- :bug: Fix resource leaks and bitmap copy bug in image processing (#3774)
- :bug: Fix Bonjour service registering with port 0 due to race with server startup (#3772)
- :bug: Fix macOS platform: `getString` memory leak and Keychain duplicate entry handling (#3770)
- :bug: Fix security layer: race condition, null safety, bounds checking, and cleanup (#3768)
- :bug: Fix duplicate retry condition and `check()` crash in sync task executors (#3766)
- :bug: Fix `TxtRecord` sort bug, non-atomic `StateFlow` mutation, and unscoped async (#3764)
- :bug: Fix sync layer: silent error swallowing, duplicate fail counting, and missing callbacks (#3762)
- :bug: Fix network plugins: encryption error handling, scope leak, and stream safety (#3760)
- :bug: Fix client API layer: error handling and logging improvements (#3758)
- :bug: Fix server routing: scope leak, path traversal, exit timeout, and logging gaps (#3756)
- :bug: Fix network layer: server error handling, `HttpClient` leak, and Bonjour robustness (#3754)
- :bug: Fix import/export stream leaks and minor code quality issues (#3752)
- :bug: Fix `FileToUrlPlugin` resource leak and simplify `MultiImagesPlugin` (#3750)
- :bug: Fix image resource leak, redundant hash computation, and silent plugin failures (#3748)
- :bug: Fix consumer/producer pipeline: `StateFlow` update, error logic, and cleanup (#3745)
- :bug: Harden pasteboard service: fix race conditions, NPE risks, and channel redesign (#3742)
- :bug: Fix `launch_at_startup` switch reading wrong config field (#3722)
- :bug: Correct `formatBytes` logic to display decimals for KB/MB/GB (#3699)

# New Features ✨

- :sparkles: Add MCP server for AI agent clipboard data access (#3839)
- :sparkles: Add MCP server settings UI with toggle and port configuration (#3841)
- :sparkles: Add `BubbleWindow` for inline text editing from Search window (#3837)
- :sparkles: Redesign `PasteTextEditContentView` with floating toolbar and undo/redo (#3860)
- :sparkles: Add inline HTML editing in `BubbleWindow` (#3862)
- :sparkles: Add cut operation with undo support for file and image paste items (#3871)
- :sparkles: Force drag-and-drop files to sync to remote download directory (#3842)
- :sparkles: Add "In Downloads" indicator and hide missing-file paste items (#3852)
- :sparkles: Sync large files directly to system download directory (#3833)
- :sparkles: Add granular sync control for specific content types (#3737)
- :sparkles: Add `mutedText` color to `ThemeExt` (#3728)
- :sparkles: Add `getPasteTypeExt()` composable helper for `PasteType` (#3726)
- :sparkles: Add `PasteTypeExt` constants for all paste types (#3724)

# UI Improvements 💄

- :lipstick: Menu item redesign by Pencil (#3720)
- :lipstick: Replace multiple theme colors with unified `CrossPasteColor` M3 theme (#3718)
- :lipstick: Use `FlowRow` for adaptive layout of image info components (#3700)
- :lipstick: Add image file size and format info components (#3697)
- :art: Improve drag-and-drop overlay visual design (#3844)

# Multiplatform · Refactor · Code Style 🔨

- :hammer: Simplify `PasteTextEditContentView` layout (#3867)
- :hammer: Add `isLoading` parameter to `DialogActionButton` for reusable loading state (#3835)
- :hammer: Refactor icon system to Material Symbols with unified `IconData` (#3739)
- :hammer: Refactor `GeneralIconButton` to use `IconButtonColors` instead of `iconColor` (#3736)
- :hammer: Refactor `SemanticColorGroup` to use Tailwind CSS color palette (#3734)
- :hammer: Improve `ImageResolution` component flexibility (#3695)
- :hammer: Clean up core data models from code-review session 1 (#3743)

# Testing 🧪

- :white_check_mark: Add 209 unit tests for core data layer (Phase 1) (#3820)
- :white_check_mark: Add 103 unit tests for business logic layer (Phase 2) (#3822)
- :white_check_mark: Add 77 unit tests for network and security layer (Phase 3) (#3824)
- :white_check_mark: Add 116 unit tests for application control layer (Phase 4) (#3825)
- :white_check_mark: Add 63 unit tests for utils and platform layer (Phase 5) (#3826)
- :white_check_mark: Add 62 unit tests for ViewModel and UI state layer (Phase 6) (#3827)
- :white_check_mark: Add 12 sync integration tests for multi-instance device lifecycle (#3818)
- :white_check_mark: Add 111 unit tests for sync layer and fix `HostInfoFilter` DNS resolution (#3816)
- :white_check_mark: Fix flaky tests by replacing `Thread.sleep` with `runTest` (#3858)

# Dependencies ⬆️

- ⬆️ **Kotlin** 2.3.0 → 2.3.10 (#3801)
- ⬆️ **Compose Plugin** 1.10.0 → 1.10.1 (#3856)
- ⬆️ **Gradle Wrapper** 8.14 → 9.3.1 (#3710)
- ⬆️ **Conveyor** 1.12 → 1.13 (#3709)
- ⬆️ **Ktor** 3.3.3 → 3.4.0 (#3701)
- ⬆️ **Logback** 1.5.25 → 1.5.31 (#3708 #3715 #3892)
- ⬆️ **atomicfu** 0.29.0 → 0.31.0 (#3702)
- ⬆️ **MockK** 1.14.7 → 1.14.9 (#3714)
- ⬆️ **download** 5.6.0 → 5.7.0 (#3802)
- ⬆️ **kotlinx-serialization-json** updated (#3703)
- ⬆️ **navigation-compose** updated (#3855)
- ⬆️ **Compose Hot-Reload** updated (#3890)

---
**Full Changelog**: https://github.com/CrossPaste/crosspaste-desktop/compare/1.2.7.1883...1.2.8.1999

# [1.2.7] - 2026-01-25
## 🚀 v1.2.7 - Emergency Hotfix

This release addresses a critical issue where devices were unable to connect or sync due to an incorrectly configured HTTP header.

### ⚠️ Critical Fixes
* **Connection Failure**: Removed manually set `Host` reserved header which caused communication timeouts in certain network environments. ([#3691](https://github.com/CrossPaste/crosspaste-desktop/pull/3691))

### 📝 Other Improvements
* **Compatibility**: Fixed `cursorColor` method for better Android compatibility. ([#3681](https://github.com/CrossPaste/crosspaste-desktop/pull/3681))
* **UI**: Fixed `FileBottomSolid` size calculation logic. ([#3686](https://github.com/CrossPaste/crosspaste-desktop/pull/3686))
* **Internal**: New `getNoDeletePasteDataFlow` for better multiplatform data handling. ([#3688](https://github.com/CrossPaste/crosspaste-desktop/pull/3688))

---
**Full Changelog**: https://github.com/CrossPaste/crosspaste-desktop/compare/1.2.6.1876...1.2.7.1883

# [1.2.6] - 2026-01-21
# Highlights 🌟

- 🎨 **Material 3 Design Overhaul**
  A massive visual upgrade transitioning the entire application to Material 3 principles. This includes a complete refactor of Settings, Device Management, Dialogs, OCR, and Import/Export interfaces for a modern, unified experience (#3516 #3529 #3546 #3560).

- ✏️ **Editable Paste Titles**
  You can now customize and edit the titles of items in your clipboard history directly via the `SidePasteTitleView`, allowing for better organization and quicker identification of important snippets (#3664).

- 🪟 **Enhanced Desktop Aesthetics**
  Significant improvements to platform-specific visuals, including a new frosted glass (translucent) background for the search window on Windows. For macOS, we've introduced support for the "Liquid Glass" effect and ensured full compatibility with macOS 26+ (#3628 #3630 #3643).

- 🔄 **Clipboard Relay & Sync**
  Introduced a new Clipboard Relay system and refactored the task submission architecture. Improved device discovery reliability with network interface fallbacks and enhanced sync status UI (#3662 #3679 #3651).

- 🛠️ **Infrastructure & Performance**
  Upgraded to Kotlin 2.3.0 and Compose 1.10.0. Optimized Bonjour service discovery and resolved critical stability issues, including a SIGSEGV crash in XToolkit for Linux users (#3510 #3637 #3521 #3645).

# Bug Fixes 🐛

- Fix SIGSEGV in XToolkit by deferring JNativeHook initialization (#3645)
- Resolve search window position flickering when opened (#3507)
- Fix layout of platform text in `DeviceRowContent` (#3552)
- Fix flaky `testDecryptionWithWrongKeyPair` unit test (#3568)
- Resolve configuration cache issues for `copyDevProperties` task (#3577)
- Fix AlwaysOnTop button theme color and size inconsistencies (#3606)
- Avoid special fonts in `DeviceDisplayName` to ensure cross-platform compatibility (#3608)
- Resolve x86 compilation error by restricting Liquid Glass to ARM64 (#3643)
- Fix nested padding issues by introducing `InnerScaffold` (#3525)
- Add network interface fallback to ensure service discovery starts reliably (#3679)

# New Features ✨

- :sparkles: Support adding tags to clipboard items (#3389)
- :sparkles: Support keyboard shortcuts for top 9 clipboard items (#3385)
- :sparkles: Add support for F13-F20 function keys (#3515)
- :sparkles: Support toggling search window via global shortcut (#3570)
- :sparkles: Implement Clipboard Relay system for advanced syncing (#3662)
- :sparkles: Enhance i18n tool with unused key detection and auto-cleanup (#3556)
- :sparkles: Implement dynamic debug mode and move About view to desktop source set (#3649)
- :sparkles: Add callback for `TrustByToken` and loading states in device dialogs (#3668)

# UI Improvements 💄

- :lipstick: Refactor major UI components (Settings, Devices, Dialog, Token, Notification, OCR) to Material 3 (#3516 #3529 #3531 #3536 #3546 #3560)
- :lipstick: Implement `AnimatedSegmentedControl` component (#3610)
- :lipstick: Add frosted glass background to search window on Windows (#3628)
- :lipstick: Refactor macOS acrylic background for macOS 26+ support (#3630)
- :lipstick: Implement dynamic semantic color support (#3604)
- :lipstick: Support editing paste item titles in `SidePasteTitleView` (#3664)
- :lipstick: Add `IncompatibleSection` to device detail view (#3519)
- :lipstick: Improve token input readability with adjusted line height (#3538)
- :lipstick: Refine device row UI and state tag appearance (#3602)

# Multiplatform · Refactor · Code Style 🔨

- :hammer: Refactor navigation to pass IDs instead of complex objects (#3579)
- :hammer: Rename QR Code to "Pairing Code" across UI and i18n (#3598)
- :hammer: Introduce multi-platform support for Export/Import Param Factories (#3585)
- :hammer: Consolidate MainWindow menu bar into `DesktopMenuBar` component (#3636)
- :hammer: Remove deprecated color contrast support from theme system (#3625 #3632)
- :hammer: Clean up unused code and i18n entries after refactoring (#3575 #3596)

# Performance ⚡

- :zap: Optimize `DesktopPasteBonjourService` discovery and throttling logic (#3521)
- :zap: Track connected host addresses and refactor `PasteClient` headers (#3676)
- :zap: Improve device refresh UX and sync reliability UI (#3651)

# Dependencies ⬆️

- ⬆️ **Kotlin** 2.2.21 → 2.3.0 (#3510)
- ⬆️ **Compose Plugin** 1.9.3 → 1.10.0 (#3637)
- ⬆️ **JBR (JetBrains Runtime)** → 21.0.9b1163.94 (#3634)
- ⬆️ **Jewel** 0.32.1 → 0.33.0 (#3528)
- ⬆️ **Logback** 1.5.20 → 1.5.25 (#3509 #3655)
- ⬆️ **MaterialKolor** 4.0.5 → 4.1.0 (#3639)
- ⬆️ **JmDNS** 3.6.2 → 3.6.3 (#3620)
- ⬆️ **ICU4J** 78.1 → 78.2 (#3656)

# Build & Tooling 👷

- :construction_worker: Update macOS runner to `macos-26` in build-release workflow (#3670)
- :construction_worker: Sync JBR version in `conveyor.conf` with actual runtime version (#3672)
- :bookmark: Update version to 1.2.6 (#3641)

---

**Full Changelog**: https://github.com/CrossPaste/crosspaste-desktop/compare/1.2.5.1787...1.2.6.1876

# [1.2.5] - 2025-12-19
# 🚀 Highlights
This is an **urgent release** addressing critical performance issues and streamlining the UI.

- ⚡️ Performance & Stability Hotfix: Addressed UI freezing and lag. We migrated database operations to an async/await pattern with an IO dispatcher and introduced HikariCP to handle concurrency. Also fixed the freeze issue during token refreshes.

- ✂️ UI Streamlining: Removed deprecated and redundant UI elements, including the Main Window clipboard display and the Central Search Window, to provide a cleaner and lighter user experience.

## What's Changed
* :zap: Convert database operations to async/await pattern with IO dispatcher by @guiyanakuang in https://github.com/CrossPaste/crosspaste-desktop/pull/3489
* :bug: Fix database concurrency issues by introducing HikariCP by @guiyanakuang in https://github.com/CrossPaste/crosspaste-desktop/pull/3491
* :lipstick: Remove clipboard display from Main Window (keep in Sidebar only) by @guiyanakuang in https://github.com/CrossPaste/crosspaste-desktop/pull/3492
* :lipstick: Remove Central Search Window implementation by @guiyanakuang in https://github.com/CrossPaste/crosspaste-desktop/pull/3493
* :hammer: Merge i18n update and sort logic into single script by @guiyanakuang in https://github.com/CrossPaste/crosspaste-desktop/pull/3495
* :bug: Resolve token refresh freeze and progress bar issues by @guiyanakuang in https://github.com/CrossPaste/crosspaste-desktop/pull/3497
* :sparkles: Make Server interface async and improve shutdown handling by @guiyanakuang in https://github.com/CrossPaste/crosspaste-desktop/pull/3499
* :bookmark: Update version to 1.2.5 by @guiyanakuang in https://github.com/CrossPaste/crosspaste-desktop/pull/3502


**Full Changelog**: https://github.com/CrossPaste/crosspaste-desktop/compare/1.2.4.1778...1.2.5.1787

# [1.2.4] - 2025-12-16
# Highlights 🌟

- 🏷️ **Clipboard Item Tagging**
  Organize your clipboard history more effectively with the new tagging system. You can now add custom tags to items, complete with a refined UI for easier management (#3389 #3392 #3415 #3442).

- 📥 **Native System Tray**
  Improved desktop integration with native system tray support, now fully implemented for both Windows and Linux environments (#3413 #3471).

- 🎨 **Visual & UX Polish**
  Introduced macOS acrylic window background effects, overhauled file/folder icons, and added image resolution overlays. Navigation has been smoothed out with consistent back-button behavior (#3404 #3426 #3444 #3448).

- ⌨️ **Smart Shortcuts & Focus**
  Added support for keyboard shortcuts to quickly access the top 9 clipboard items. Window focus logic has been significantly improved for better stability and automatic monitoring (#3378 #3384 #3385).

- ⚡ **Performance & Stability**
  Significant under-the-hood optimizations to reduce application size and memory footprint, resulting in a snappier and more responsive experience (#3383 #3399 #3408).

- 🐧 **Linux AppImage Support**
  Greatly enhanced Linux compatibility with official AppImage packaging support, simplifying installation across different distributions (#3456).

# Bug Fixes 🐛

- Resolve SIGSEGV crash due to unsafe window pointer usage (#3473)
- Fix crash caused by process-based hostname retrieval (#3393)
- Fix migration crash for `PasteTagEntity` (#3392)
- Ensure Settings window automatically appears on first launch (macOS) (#3395)
- Fix drag-and-drop performance issue in `SidePasteItemView` (#3402)
- Prevent tagged items from being removed during deduplication (#3415)
- Fix compilation errors in securedao (#3421)
- Resolve aliasing in side app source icons due to crop transformation (#3432)
- Fix sync control parameter inconsistency in device detail view (#3452)
- Fix incorrect directory size check allowing large folders to bypass limit (#3465)
- Patch incorrect icon paths in decorated window icon keys via reflection (#3469)
- Fix tray menu showing search window instead of main window (#3471)
- Improve main window focus behavior when already visible (#3477)
- Add cache invalidation for native window handles to prevent stale state (#3479)
- Reset active image memory reclamation logic (#3483)


# New Features ✨

- :sparkles: Support adding tags to clipboard items (#3389)
- :sparkles: Support keyboard shortcuts for top 9 clipboard items (#3385)
- :sparkles: Add support for automatic window focus change monitoring (#3384)
- :sparkles: Implement native tray support for Windows and Linux (#3413)
- :sparkles: Add AppImage packaging support for Linux builds (#3456)
- :sparkles: improve window focus logic and display interface (#3378)

# UI Improvements 💄

- :lipstick: Implement acrylic window background (macOS) (#3404)
- :lipstick: Overhaul file and folder icons with modern aesthetics (#3426)
- :lipstick: Display image resolution overlay in side panel (#3444)
- :lipstick: Refine tag chip styling and sizing consistency (#3442)
- :lipstick: Improve text styling with consistent line height configuration (#3430)
- :lipstick: Implemented `FileBottomSolid` to display file info in preview (#3436)
- :lipstick: Refactored `UrlBottomSolid` for layout harmony and readability (#3434)
- :lipstick: Implement navigation improvements with graph routing (#3448)
- :lipstick: Enhance UI consistency with unified background styling (#3450)

# Multiplatform · Refactor · Code Style 🔨

- :hammer: Replace Guava with Caffeine to reduce dependency size (#3383)
- :hammer: Consolidate theme logic into unified reactive `ThemeState` (#3397)
- :hammer: Migrate to centralized reactive `AppSizeValue` architecture (#3410)
- :hammer: Refactor UI context management and optimize window state access (#3419)
- :hammer: Refactor bottom solid components to support multiplatform usage (#3440)
- :hammer: Replace `jSystemThemeDetector` with native Compose API (#3446)
- :hammer: Refactor `NavigationEvent` from sealed class to interface pattern (#3454)

# Performance ⚡

- :zap: Optimize UI performance by eliminating unnecessary state (#3399)
- :zap: Add `@Stable` annotation to `PasteDetailInfoItem` (#3406)
- :zap: Optimize image loading and UI performance across multiple components (#3408)
- :zap: Optimize `AppSourceIcon` layout stability and scaling logic (#3417)
- :zap: Optimize scroll performance in `SidePasteboardContentView` (#3438)

# Dependencies ⬆️

- ⬆️ **Jewel** 0.31.0 → 0.32.1 (#3373 #3458)
- ⬆️ **Ktor** 3.3.2 → 3.3.3 (#3386)
- ⬆️ **SQLDelight** 2.0.2 → 2.2.1 (#3343 #3346)
- ⬆️ **Okio** 3.16.2 → 3.16.4 (#3411)
- ⬆️ **Ktlint** 13.1.0 → 14.0.1 (#3457)
- ⬆️ **Ph-css** 8.0.1 → 8.1.1 (#3459)
- ⬆️ **ZXing Core** 3.5.3 → 3.5.4 (#3463)
- ⬆️ **Mockk** 1.14.6 → 1.14.7 (#3462)

# Build & Tooling 👷

- :arrow_up: Upgrade Gradle to 8.14 (#3428)
- :arrow_up: Update Conveyor build action to v21.0 (#3481)
- :construction_worker: Cache Kotlin Native dependencies to speed up build (#3423)
- :package: Set JNA library path for production builds (#3467)
- :loud_sound: Add AWT exception handler for better crash logging (#3475)

---

**Full Changelog**: https://github.com/CrossPaste/crosspaste-desktop/compare/1.2.3.1718...1.2.4.1778

# [1.2.3] - 2025-11-25
# Highlights 🌟

- 🖼️ **Optical Character Recognition (OCR)**
  Extract text directly from images in your clipboard. This new module supports asynchronous processing and includes guidance for first-time usage (#3302 #3306 #3321).

- 🧩 **Extensions Framework**
  Added the foundation for the upcoming extension system, including a new management UI, download module, and configurable proxy support (#3291 #3295 #3296).

- 🔎 **Enhanced Search Experience**
  You can now edit text content directly in the search window and use multi-select to paste multiple items in sequence based on your selection order (#3277 #3279).

- 📂 **File & Folder Visualization**
  The clipboard history now visually renders file and folder items, making it easier to distinguish and manage file-based content (#3262).

- 🧹 **Smarter Memory Management**
  The app now proactively clears loaded image memory when the window is hidden, reducing the memory footprint during background operation (#3368).

# Bug Fixes 🐛

- Fix search window pasteboard type selection not resetting (#3226)
- Disable gzip encoding to restore expected decryption behavior (#3242)
- Resolve `zh_hant.properties` not being updated in i18n batch script (#3245)
- Correct middle ellipsis logic for URL text display (#3249)
- Fix NPE in `writePasteboard` when no PasteText item exists (#3256)
- Fix client interface version recognition (#3283)
- Fix settings UI width and scrollbar position (#3293)
- Fix typo in Linux log path (`~/.local/share`) (#3309)
- Bring main window to front when displaying token (#3311)
- Establish bidirectional connection automatically when connecting devices (#3313)
- Resolve secret key loss or mismatch issues (#3316)
- Improve display text when network info is unavailable (#3315)
- Fix incorrect sha512 hash for JBR SDK (#3328)
- Fix macOS app signing, notarization, and runtime compatibility issues (#3332 #3338 #3342)
- Ensure search window displays on top (#3348)
- Restrict URL preview to `text/html` and `application/xml` only (#3350)
- Fix connection address overwrite after failed host connection attempt (#3354)
- Prevent duplicate cropping in `CropTransformation` (#3364)
- Prioritize longer key combinations to prevent prefix misfires (#3371)

# New Features ✨

- :sparkles: Support editing text pasteboard in search window (#3277)
- :sparkles: Support multi-select paste based on selection order (#3279)
- :sparkles: Support rendering files and folders for file pasteboard (#3262)
- :sparkles: Add OCR Module for text extraction (#3302)
- :sparkles: Add extension UI and download module (#3291 #3296)
- :sparkles: Support configurable extension proxy (#3295)
- :sparkles: Support automatic redirect in HttpClient (#3360)

# UI Improvements 💄

- :lipstick: Add extension management UI (#3291)
- :lipstick: Add guidance for first-time OCR usage (#3321)

# Multiplatform · Refactor · Code Style 🔨

- :hammer: Remove Debug UI, enable debugging via hot reload (#3228)
- :hammer: Migrate navigation to event-driven architecture (#3230 #3238)
- :hammer: Clean up `libs.versions.toml` (#3234)
- :hammer: Update `UrlBottomSolid` implementation for multi-platform reuse (#3251)
- :hammer: Rename native target to `nativeApp` to avoid template conflicts (#3264)
- :hammer: Extract `SidePasteTitleView` to separate file (#3281)
- :hammer: Modify sync address update strategy to use merge instead of overwrite (#3322)
- :hammer: Remove `JvmStatic` annotation from `commonMain` (#3352)
- :hammer: Abstract `ResourcesClient` implementation for multiplatform reuse (#3356 #3358)
- :hammer: Improve Coil3 fetch implementation (#3366)

# Performance ⚡

- :zap: Make text extraction and related operations asynchronous (#3306)
- :zap: Clear loaded image memory proactively when window is hidden (#3368)
- :zap: Improve image generation service with proper cancellation handling (#3247)

# Dependencies ⬆️

- ⬆️ **Kotlin** 2.2.10 → 2.2.21 (#3257 #3287)
- ⬆️ **Compose Plugin** 1.9.0-rc01 → 1.9.3 (#3232 #3203 #3297)
- ⬆️ **Compose Hot Reload** 1.0.0-beta06 → 1.0.0-rc02 (#3254 #3285)
- ⬆️ **Navigation Compose** 2.9.0-rc02 → 2.9.1 (#3253 #3275)
- ⬆️ **Ktor** 3.2.3 → 3.3.2 (#3236 #3268 #3303)
- ⬆️ **Jewel** 0.30.0 → 0.31.0 (#3273 #3304)
- ⬆️ **Lifecycle** 2.9.3 → 2.9.6 (#3265 #3284)
- ⬆️ **JNA** 5.17.0 → 5.18.1 (#3259 #3267)
- ⬆️ **Logback** 1.5.18 → 1.5.20 (#3266 #3274)
- ⬆️ **Guava** 33.4.8-jre → 33.5.0-jre (#3269)
- ⬆️ **Okio** 3.16.0 → 3.16.2 (#3286)
- ⬆️ **ICU4J** 77.1 → 78.1 (#3299)
- ⬆️ **Ph-css** 8.0.0 → 8.0.1 (#3298)
- ⬆️ **FileKit Dialogs** 0.11.0 → 0.12.0 (#3369)
- ⬆️ **Mockk** 1.14.5 → 1.14.6 (#3344)

# Build & Tooling 👷

- :wrench: Upgrade minimum macOS version to 14.0.0 (#3336)
- :arrow_up: Upgrade JBR version to 21.0.8b1163.69 (#3320 #3330)
- :arrow_up: Upgrade conveyor action version to 20.0 and compatibility level to 19 (#3334 #3340)
- :bug: Fix sign native libraries in tesseract JAR for macOS x86_64 notarization (#3342)

---

**Full Changelog**: https://github.com/CrossPaste/crosspaste-desktop/compare/1.2.2.1640...1.2.3.1718

# [1.2.2] - 2025-9-11
# Highlights 🌟

- 🚀 **Database Performance Boost**  
  SQLite is now tuned for better concurrency and responsiveness, improving overall app performance (#3102).

- 🌐 **Network Interface Selection**  
  Choose which network interface CrossPaste uses for discovery and sync, giving you more control in multi-network environments (#3142).

- 📱 **Smarter Device Management**  
  Preview device details before adding, and enjoy a fully reactive sync service with dynamic polling intervals for faster, more reliable connections (#3148 #3166 #3171).

- 📝 **Rich Text Everywhere**  
  HTML clipboard rendering now uses rich text (no Chrome dependency) and RTF is also rendered via RichText (#3193 #3198).

- 🍏 **Native macOS Status Bar**  
  CrossPaste now integrates with the macOS system status bar, offering a more seamless and intuitive interaction experience(#3222).

# Bug Fixes 🐛

- Handle invalid filename characters when syncing files from other systems to Windows (#3074)
- Fix FileNameNormalizer extension preservation and dot-only files (#3076)
- Fix PasteDataScopeImpl implementation (#3132)
- Fix PasteShimmer UI in side window (#3139)
- Fix UI recomposition for `AsyncImagePainter` state changes (#3189)
- Fix infinite sync update loop by checking for actual changes (#3175)
- Integrate Swift dylib compilation with resources pipeline to work with atomicfu (#3181)
- Fix murmurhash3 compilation warnings (#3183)
- Fix issue where scrolling clipboard doesn’t load more items (#3200)
- Normalize HSL inputs for `hslToColor` in `getAdaptiveColor` (#3207)
- Improve error handling in paste data deserialization (#3213)
- Ensure `PasteItem.fromJson` catches exceptions (#3217)
- Remove locale-dependent format directives for cross-platform compatibility (#3221)


# New Features ✨

- :sparkles: Native app menu support (#3053)
- :sparkles: Reintroduce “Clear All Paste” button (#3114)
- :sparkles: Dynamic polling interval adjustment in sync manager (#3171)
- :sparkles: Use rich text rendering for HTML clipboard content (#3193)
- :sparkles: Use RichText to render RTF clipboard content (#3198)
- :sparkles: Smart contrast colour utility for colour palette (#3219)
- :sparkles: Network interface selection (#3142)
- :sparkles: View device details before adding nearby devices (#3148)
- :sparkles: Multi-language support for date/time formatting (#3096)


# UI Improvements 💄

- :lipstick: Newly added clipboard items are displayed at the top (#3073)


# Multiplatform · Refactor · Code Style 🔨

- :hammer: Make `toRGBString` compatible with iOS (#3069)
- :hammer: Move `JsonUtils` to `commonMain` for cross-platform reuse (#3085)
- :hammer: Refactor `build.gradle.kts` and reorder dependencies (#3087)
- :hammer: Migrate shared code to `shared` module in preparation for CLI module creation (#3089)
- :hammer: Rewrite all `toByteArray` calls to `encodeToByteArray` (#3112)
- :hammer: Remove `PasteboardViewProvider` interface to simplify implementation (#3124)
- :hammer: Add `PasteDataScope` and refactor preview views structure (#3130)
- :hammer: Refactor pasteboard-related UI using `PasteDataScope` to avoid repeatedly passing `PasteData` between UI functions (#3136)
- :hammer: Optimize `PasteDataScope` related code (#3138)
- :hammer: Abstract `ResourcesClient` to support multi-platform resource reading (#3150)
- :hammer: Rename `ImageWriter` to `imageHandler` and add read methods (#3152)
- :hammer: Standardize image resource file naming convention (#3154)
- :hammer: Make the device sync service fully reactive (#3166)
- :hammer: Extract independent connection maintenance logic for connected and pending devices (#3168)
- :hammer: Resolve multiplatform support issues for network components (#3177)
- :hammer: Rewrite `SyncPollingManager` to use atomic instead of `@Volatile` for multiplatform support (#3179)
- :hammer: Implement screen navigation and switching in base class for multi-platform reuse (#3185)
- :hammer: Rewrite `ColorUtils` based on ph-css source code (#3191)
- :hammer: Make `border` parameter optional in `HighlightedCard` component (#3187)
- :hammer: Update theme definitions to use non-deprecated `JewelTheme` API (#3215)


# Performance ⚡

- :zap: Configure SQLite for better concurrency and performance (#3102)
- :zap: Optimize search state management and add throttling for pagination (#3211)


# Dependencies ⬆️

- ⬆️ **Compose** 1.8.3 → 1.9.0 (#3118)
- ⬆️ **Compose-plugin** 1.8.2 → 1.9.0-rc01 (#3161)
- ⬆️ **Compose-hot-reload** 1.0.0-beta04 → 1.0.0-beta06 (#3119 #3162)
- ⬆️ **Kotlin** 2.2.0 → 2.2.10 (#3122)
- ⬆️ **Ktor** 3.2.2 → 3.2.3 (#3079)
- ⬆️ **Okio** 3.15.0 → 3.16.0 (#3083)
- ⬆️ **Ksoup** 0.2.4 → 0.2.5 (#3077)
- ⬆️ **Coil** 3.2.0 → 3.3.0 (#3057)
- ⬆️ **Jewel** 0.28.0-252.15920 → 0.30.0-252.26252 (#3056 #3195)
- ⬆️ **Lifecycle** 2.9.1 → 2.9.3 (#3121 #3159)
- ⬆️ **Koin** 4.1.0 → 4.1.1 (#3194)
- ⬆️ **kotlin-logging** 7.0.7 → 7.0.13 (#3080 #3099 #3145)
- ⬆️ **filekit-dialogs** 0.10.0-beta04 → 0.11.0 (#3078 #3205)
- ⬆️ **ktlint-gradle** 13.0.0 → 13.1.0 (#3144)
- ⬆️ **snakeyaml** 2.4 → 2.5 (#3158)
- ⬆️ **jmdns** 3.6.1 → 3.6.2 (#3160)


# Documentation 📝

- :memo: Update product images in README (#3055)
- :memo: Update main image in README (#3065)
- :memo: Add missing `alt` text to Chinese ad images (#3060)
- :memo: Update project documentation (#3116)


# Build & Tooling 👷

- :construction_worker: Switch Claude review to comment-triggered workflow (#3062)
- :construction_worker: Reject dependencies with “dev” versions in Gradle resolution strategy (#3202)


# Tests ✅

- :white_check_mark: Enhance `SecureMessageProcessor` unit tests (#3106)
- :white_check_mark: Add comprehensive unit tests for `DefaultPasteSync` `ProcessManager` and `PasteSingleProcessImpl` (#3108)
- :white_check_mark: Enhance `GeneralSyncManager` unit tests with scope updates (#3110)


---

**Full Changelog**: <https://github.com/CrossPaste/crosspaste-desktop/compare/1.2.1.1551...1.2.2.1640>

# [1.2.1] - 2025-7-26
# Highlights 🌟

- 🌐 **New Language Support**
  German(`de`), French(`fr`), Korean(`ko`), andTraditionalChinese(`zh‑Hant`) are now fully localised, with automatic English fallback for missing keys.

- 🔍 **Sidebar Search Window 2.0**
  brand‑new edge window with its own search box, clipboard list, settings button, context menu, and favourite toggle for lightning‑fast access.

- 🚀 **One‑Click Online Upgrade**
  trigger the updater directly from the app UI to stay current without manual downloads.

- 🖼️ **Smarter Content Intelligence**
  Open Graph title/image extraction, rendered‑image background‑colour analysis, and ANSI text fallback for legacy apps.

- ✨ **Drag & Drop Everywhere**
  drag clips into/out of the *main* window, plus double‑click‑to‑paste in the side window.

- 💄 **Customise Your Workspace**
  choose a custom font, keep the main window *always on top*, pin windows, and enjoy refined icons & dialogs.

# Bug Fixes 🐛

- Fixed uncaught exceptions during update checks (#2863)
- Prevented clipboard‑rendering info from syncing across devices (#2896)
- Resolved Linux tray menu and mixed‑window display issues (#2916 #2920)
- Corrected search footer text contrast and hover states (#2922 #3015)
- Fixed JSON serialisation, index mismatches, RTF icon assignment, and tutorial‑button visibility (#2936 #2973 #2968 #2984)
- Patched HTML background‑colour extraction & alpha‑loss bugs, plus added extra tests (#2988 #2990)
- Fixed window not showing after minimisation on some platforms (#3008)
- Resolve mutex lock conflicts in concurrent operations (#3035)

# New Features ✨

- :sparkles: Native system file dialogs for open/save operations (#2954)
- :sparkles: Right‑click context menu, creation‑time stamp, character count, and hints for pasteboards (#2938 #2946 #2986 #2960)
- :sparkles: Hot‑reload support via *compose‑hot‑reload* (#2994)
- :sparkles: Check‑for‑updates menu item added to the main window (#3023)
- :sparkles: Support for dragging clipboard data across apps in side window (#2956)
- :sparkles: Add configurable paste behavior with primary/mixed type (#3047)

# UI Improvements 💄

- :lipstick: Movable decorative bar and improved notification/dialog layout (#2911 #2942 #2943)
- :lipstick: Settings button in edge window and polished basic‑type icons (#2940 #2944)
- :lipstick: Added quit option, favourite star, pin button, and better default widths (#2951 #2996 #2998 #3000)
- :lipstick: Adjusted dialog width, font‑settings icon, and check‑for‑updates visibility (#2943 #2963 #3023)
- :lipstick: Enhance side panel UI with smart image display and improved sync handling (#3037)
- :lipstick: Improve Windows icon extraction implementation (#3039)

# Multiplatform · Refactor · Code Style 🔨

- :hammer: Replaced **Jsoup → Ksoup** for full multiplatform parsing (#2887)
- :hammer: Unified search window instance & refactored window‑position logic (#2918 #2929)
- :hammer: Moved JVM‑only code and PNG analyser to `DesktopMain`, removed unused SVGs and search methods (#2965 #3021 #2971 #2910)
- :hammer: Created `DesktopClient`, revamped `AppConfig`, `RenderingService`, and general code structure (#2890 #2866 #2894 #2892)

# Performance ⚡

- :zap: Faster page‑title / image extraction and smoother decoration‑bar drag (#2958 #3009)

# Dependencies ⬆️

- ⬆️ **Compose** 1.8.2 → 1.8.3 (#2876)
- ⬆️ **Compose‑plugin** 1.8.1 → 1.8.2 (#2933)
- ⬆️ **Kotlin** 2.1.21 → 2.2.0 (#2899)
- ⬆️ **Ktor** 3.1.3 → 3.2.2 (#2857 #2932 #3026)
- ⬆️ **Okio** 3.12.0 → 3.15.0 (#2858 #2897 #2931)
- ⬆️ **Lifecycle** 2.9.0 → 2.9.1 (#2878)
- ⬆️ **Koin** 4.0.4 → 4.1.0 (#2852)
- ⬆️ **ktlint‑gradle** 12.3.0 → 13.0.0 (#2980)
- ⬆️ **mockk** 1.14.2 → 1.14.5 (#2879 #2982 #3027)
- ⬆️ **webp‑imageio** 0.10.0 → 0.10.2 (#2901 #2982)
- ⬆️ **kotlinx‑serialization‑json** 1.8.1 → 1.9.0 (#2900)
- ⬆️ **kotlinx‑datetime** 0.6.2 → 0.7.1‑0.6.x‑compat (#2979)
- ⬆️ Plus routine bumps to **cryptography**, **turbine**, **coroutines‑core**, **jSystemThemeDetector**, and *compose‑hot‑reload* (#2930 #2855 #2981 #2934 #3028).

# Documentation 📝

- :memo: Added **CLAUDE.md** and refreshed README (#2908 #2854)

# Build & Tooling 👷

- :construction_worker: Introduced **Claude Code Review** workflow, approval bindings, and auto‑close logic (#2909 #2924 #2927 #2953)
- :construction_worker: Split OSS upload steps to prevent CI time‑outs and updated OSS plugin (#3031 #3033)
- :construction_worker: Added hot‑reload build support (#2994)

# Contributors ✨

- **@amir1376** implemented the new *font‑settings* feature (#2961) — welcome aboard!

---

**Full Changelog**: <https://github.com/CrossPaste/crosspaste-desktop/compare/1.2.0.1444...1.2.1.1551>

# [1.2.0] - 2025-6-10
# Highlights 🌟

- 🌐 **Persian Language Support Added**  
  We’ve added support for Persian language (`fa`) to enhance international accessibility.

- 🔍 **Enhanced Search Matching with ICU4J Tokenizer**  
  Search experience has been significantly improved, especially for multi-language content and punctuation-based queries.

- 🧠 **Auto-generate Text from HTML/RTF Clipboard**  
  When copying rich content, plain text versions are now auto-generated to improve compatibility and fallback rendering.

- 🔄 **HTML Charset Enforcement**  
  Introduced `ensureHtmlCharsetUtf8` to fix issues with incorrect HTML encoding on some systems, ensuring consistent display.

- 💡 **New Guide Auto Switch**  
  The usage guide now updates automatically when switching languages.

- 🤝 **Recommendation Sharing Feature**  
  Easily share CrossPaste with your friends through a new built-in recommendation system — complete with preview and social support.

- 🧪 **New Unit Tests**  
  Added tests to ensure correct behavior of reactive `SyncRuntimeInfoDao` flows.

- 🐧 **Ubuntu `.deb` Package Support**  
  Official `.deb` installation support for Ubuntu 22.04 LTS (Jammy) and later has been added.

# Bug Fixes 🐛

- :bug: Prevent crash by specifying parentCoroutineContext during port conflict (#2711)
- :bug: Fix IP address not syncing to other devices after DHCP reassignment (#2715)
- :bug: Fix `AbstractMethodError` in ExpandView (#2727)
- :bug: Correct background and icon color contrast (#2742)
- :bug: Fix ExpandView compilation issue on Android Compose (#2738)
- :bug: Revert `TextOverflow` to `Ellipsis` from `Clip` for better layout (#2812)
- :bug: Fix UI regression issues after refactor (#2824)
- :bug: Fix `rememberCoroutineScope` exits due to partial UI switching (#2839)
- :bug: Fix device sync control settings being unintentionally overridden (#2835)
- :bug: Fix `SQLITE_BUSY` caused by bulk deletion (#2840)
- :bug: Fix incorrect icon color in search window (#2793)

# New Features ✨

- :sparkles: Add auto-scroll support in `ExpandView` component (#2795)
- :sparkles: Automatically update guide when switching languages (#2760)
- :sparkles: Add recommendation sharing feature (#2770)
- :sparkles: Auto-generate missing text from HTML/RTF clipboard (#2828)
- :sparkles: Add `ensureHtmlCharsetUtf8` method (#2831)

# UI Improvements 💄

- :lipstick: Create HighlightedCard display effect (#2703)
- :lipstick: Add vertical alignment to `DialogButtonsView` button row (#2713)
- :lipstick: Add leading/trailing icons to search input (#2723)
- :lipstick: Change toast text alignment to `Justify` (#2744)
- :lipstick: Use Divider (1.dp) in search window (#2777)
- :lipstick: Switch default theme to blue Sea (#2779)
- :lipstick: Enable scrolling for long text (#2791)
- :lipstick: Improve `DialogButtonsView` spacing (#2801)
- :lipstick: Implement spacing and sizing system (#2803)
- :lipstick: Remove `CursorWait` usage for better UX (#2842)
- :lipstick: Improve `ToastView` message readability (#2844)
- :lipstick: Polish UI visuals and enhance consistency (#2787)

# Multiplatform & Refactor & Code Style 🔨

- :hammer: Improve ExpandView to support platform-specific rendering (#2725)
- :hammer: Use DI to provide Platform instance for better testability (#2740)
- :hammer: Migrate deprecated APIs for JDK 21 compatibility (#2752)
- :hammer: Move core initialization to `InitPasteDataService` (#2754)
- :hammer: Obtain `DesktopAppSize` through DI framework (#2756)
- :hammer: Rename `GlobalCopywriter` with platform prefix (#2762)
- :hammer: Remove `MobileExpandView` since desktop is fully handled (#2797)
- :hammer: Limit `windowDecorationHeight` to desktop (#2799)
- :hammer: Extract all `textStyles` to `AppUIFont` (#2805)
- :hammer: Replace `toByteArray` with `encodeToByteArray` for UTF-8 (#2808)
- :hammer: Remove redundant `encodeToString` import (#2810)
- :hammer: Extract UI constants to `AppUISize`, `AppUIFont`, `AppUIColors` (#2822)

# Dependencies ⬆️

- ⬆️ Bump `org.jsoup:jsoup` from 1.19.1 → 1.20.1 (#2709)
- ⬆️ Bump `io.mockk:mockk` from 1.14.0 → 1.14.2 (#2708)
- ⬆️ Bump `ktor` from 3.1.2 → 3.1.3 (#2716)
- ⬆️ Bump `compose` from 1.8.0 → 1.8.1 → 1.8.2 (#2718, #2772)
- ⬆️ Bump `compose-plugin` from 1.7.3 → 1.8.0 → 1.8.1 (#2719, #2771)
- ⬆️ Bump `lifecycle` from 2.8.4 → 2.9.0-beta01 → 2.9.0 (#2717, #2721)
- ⬆️ Bump `coil` from 3.1.0 → 3.2.0 (#2732)
- ⬆️ Bump `kotlin` from 2.1.20 → 2.1.21 (#2733)
- ⬆️ Bump `sqldelight` from 2.0.2 → 2.1.0, then reverted (#2750, #2764)
- ⬆️ Bump `org.jlleitschuh.gradle.ktlint` from 12.2.0 → 12.3.0 (#2773)
- ⬆️ Bump `com.valentinilk.shimmer:compose-shimmer` from 1.3.2 → 1.3.3 (#2781)
- ⬆️ Bump `okio` from 3.11.0 → 3.12.0 (#2817)
- ⬆️ Bump `webp-imageio` from 0.9.0 → 0.10.0 (#2819)

# Documentation 📝

- :memo: Update changelog (#2699)
- :memo: Add DeepWiki official badge (#2785)
- :memo: Add sponsors and GitHub star buttons to README (#2768)

# Build & Tooling 👷

- :construction_worker: Upgrade JBR to 21.0.7b968.13 (#2745)
- :construction_worker: Upgrade Conveyor to 18.1 and JDK to 21 (#2816)

# New Contributors ✨

- @amir1376 made their first contribution in https://github.com/CrossPaste/crosspaste-desktop/pull/2728

**Full Changelog**: https://github.com/CrossPaste/crosspaste-desktop/compare/1.1.2.1375...1.2.0.1444

# [1.1.2] - 2025-5-1
## Highlights 🌟

- 🔄 **Migrated from Realm to SQLite (SQLDelight) — Breaking Change:**  
  We've officially transitioned from Realm to SQLDelight for data persistence. Since Realm is no longer actively maintained, this change was essential for long-term project sustainability, better multiplatform support, and open tooling integration.  
  ⚠️ **Important:** Due to differences in storage structure, this is a **non-backward-compatible** migration. Pasteboard data from previous versions will **not be visible** in the new version. However, your old data has **not been deleted** — it's still safely stored in the previous Realm database files.

- 📱 **Android Version Under Review:**
  The Android version of CrossPaste is currently under review on Google Play. A corresponding changelog will be released when it's officially available.

- 📤📥 **Import & Export Support for Pasteboard:**  
  You can now import and export your pasteboard data, making backups, transfers, and marketing scenarios much more flexible and powerful.

- 🔁 **Fully Shared `commonMain` Logic with Mobile Platforms:**  
  Core logic is now truly multiplatform. We've refactored platform-specific code to maximize reuse across desktop and mobile environments, simplifying future feature rollouts and bug fixes.

- 🎨 **Refreshed Logo and Branding:**  
  The app now features a modernized logo and consistent branding across platforms, including SVG-based rendering to ensure crisp display at any resolution.

- 🧩 **Multiple Fixes to Data Migration Logic:**  
  We've resolved several issues related to migration paths, including path resolution, cleanup logic, and data consistency across updates.


## Bug Fixes 🐛
- :bug: Fix iOS build failure due to missing encodeToString import (#2413)
- :bug: Remove remaining realm information from the project (#2432)
- :bug: Fix mobile build failure due to missing encodeToString import (#2439)
- :bug: Fix the issue of failing to clean up pasteboard files (#2443)
- :bug: Single device sync failure triggers unnecessary re-sync of all connected devices (#2448)
- :bug: Fix ErrorCode code style warnings (#2450)
- :bug: Fix missing verification code on first device connection (#2462)
- :bug: Enhance pasteboard search functionality (#2464)
- :bug: Fix internationalization text translation (#2466)
- :bug: Fix toast animation issue on Android platform (#2497)
- :bug: Fix the issue where the pasteboard favorites interface doesn't respond in a timely manner (#2483)
- :bug: Storage settings interface cannot view saved pasteboard storage details (#2490)
- :bug: Fix the issue where selecting paste type in the export interface doesn't respond (#2540)
- :bug: Fix the bug caused by concurrent access to state variables (#2535)
- :bug: Fix the issue of incorrect colors in multiple view contents (#2536)
- :bug: Fix the issue where MutableMap doesn't support computeIfAbsent on iOS (#2538)
- :bug: Fix X11 connection leaks (#2594)
- :bug: Fix inconsistent icon sizes in ToastView (#2634)
- :bug: Fix image file clipboard transfer to web applications (#2638)
- :bug: Fix inconsistent font in color palette compared to other views (#2589)
- :bug: Fix HTML and RTF pasteboard view rendering issues (#2592)
- :bug: Fix all compilation warnings (#2658)
- :bug: Fix missing dylib files in build output (#2679)
- :bug: Fix incorrect dylib verification path (#2681)
- :bug: Fix the storage path cannot be modified (#2682)
- :bug: Fix Windows application restart service (#2683)
- :bug: Fix logical errors in data migration checks (#2685)
- :bug: Fix process termination waiting issue in Windows startup script (#2688)
- :bug: Close database driver before data migration (#2690)
- :bug: Fix the issue of incomplete Windows right-click menu display (#2691)
- :bug: Fix file deletion issue in storage directory copying caused by identical hash values (#2693)
- :bug: Fix the issue where the selected underline in TabsView does not match the text length (#2695)
- :bug: Fix issue where Window right-click menu height doesn't account for padding (#2697)

## New Features ✨
- :sparkles: Support pasteboard import and export (#2453)
- :sparkles: Add specific callbacks for key actions to support flow control (#2475)
- :sparkles: Notification bar supports displaying multiple messages (#2479)
- :sparkles: Toast view now supports displaying body content (#2491)
- :sparkles: Add method to PasteText for reading first 256 characters for preview (#2504)
- :sparkles: Support searching based on source and filename (#2527)
- :sparkles: Periodically clean up executed task information in the database (#2532)
- :sparkles: Automatically close token and hide interface after successful connection (#2631)
- :sparkles: First launch, automatically add tutorial pasteboard examples (#2641)
- :sparkles: Add marketing definition support to search interface (#2655)
- :sparkles: Create RatingPromptManager for callbacks on important operations (#2669)
- :sparkles: Add suspendRetry function for handling suspending retry operations (#2673)

## UI Improvements 💄
- :lipstick: Create DialogView and TokenView to make the overall interface composition clearer (#2481)
- :lipstick: Optimized Dialog UI effects (#2555)
- :lipstick: Improve font styles for empty screen prompts and paste type selection area (#2630)
- :lipstick: Enhanced date picker UI (#2563)
- :lipstick: Add text alignment and line height to ToastView title (#2602)
- :lipstick: use new version of logo (#2608)
- :lipstick: Remove icons, render all project logos using SVG (#2610)
- :lipstick: Improve pasteboard display effects (#2587)
- :lipstick: Center align button text in dialog buttons (#2649)
- :lipstick: Text and icons in the device bar should adapt to background color changes (#2557)

## Multiplatform & Refactor & Code Style 🔨
- :hammer: Change AppLockState to interface to allow extension by other platforms (#2411)
- :hammer: Migrate from Realm to SQLDelight for data persistence (#2425)
- :hammer: Move writeFile function to interface for multiplatform support (#2460)
- :hammer: Support null duration in sendNotification API (#2469)
- :hammer: Replace ScreenType enum with sealed interface pattern (#2499)
- :hammer: Move DesktopToastManager implementation to commonMain (#2488)
- :hammer: Refactor import/export implementation to facilitate mobile reuse (#2547)
- :hammer: Refactor pasteboard type icons (#2552)
- :hammer: Convert verbose if-else returns to expression form (#2668)
- :hammer: Change SyncManager to explicit lifecycle control (#2660)
- :hammer: Remove unused resource files (#2662)
- :hammer: Move MainSettingsContentView to desktop implementation (#2622)
- :hammer: Abstract PasteDialog to allow different implementations for desktop and mobile (#2566)
- :hammer: Enhance device sync control with improved conditional checks (#2514)
- :hammer: Extract nowInstant() method to improve testability (#2452)
- :hammer: Replace try catch blocks with runCatching style (#2525)
- :hammer: Enhance device detail page and tab styling (#2636)
- :hammer: Align Koin and Koin-compose versions for consistent dependency management (#2582)
- :hammer: Optimize Kotlin code using scope functions and null-safety operators (#2628)
- :hammer: Add name property to ScreenType (#2559)

## Dependencies ⬆️
- ⬆️ Bump compose from 1.7.6 → 1.7.7 → 1.7.8 → 1.8.0
- ⬆️ Bump com.valentinilk.shimmer:compose-shimmer from 1.3.1 to 1.3.2
- ⬆️ Bump coil from 3.0.4 to 3.1.0
- ⬆️ Bump io.github.oshai:kotlin-logging from 7.0.3 → 7.0.4 → 7.0.5 → 7.0.6 → 7.0.7
- ⬆️ Bump ktor from 3.0.3 → 3.1.0 → 3.1.1 → 3.1.2
- ⬆️ Bump org.yaml:snakeyaml from 2.3 to 2.4
- ⬆️ Bump org.jetbrains.kotlinx:kotlinx-datetime from 0.6.1 to 0.6.2
- ⬆️ Bump kotlin from 2.0.21 → 2.1.10 → 2.1.20
- ⬆️ Bump org.jlleitschuh.gradle.ktlint from 12.1.2 to 12.2.0
- ⬆️ Bump ch.qos.logback:logback-classic from 1.5.16 → 1.5.17 → 1.5.18
- ⬆️ Bump io.mockk:mockk from 1.13.16 → 1.13.17 → 1.14.0
- ⬆️ Bump org.jsoup:jsoup from 1.18.3 to 1.19.1
- ⬆️ Bump jna from 5.16.0 to 5.17.0
- ⬆️ Bump org.jmdns:jmdns from 3.6.0 to 3.6.1
- ⬆️ Bump com.google.guava:guava from 33.4.0 → 33.4.5 → 33.4.6 → 33.4.7 → 33.4.8
- ⬆️ Bump io.github.z4kn4fein:semver from 2.0.0 to 3.0.0
- ⬆️ Bump androidx.compose.material3:material3 from 1.3.1 to 1.3.2
- ⬆️ Bump com.squareup.okio:okio from 3.10.2 to 3.11.0
- ⬆️ Bump org.jetbrains.kotlinx:kotlinx-serialization-json from 1.8.0 to 1.8.1
- ⬆️ Bump kotlinx-coroutines from 1.10.1 to 1.10.2
- ⬆️ Bump koin from 4.0.2 → 4.0.3 → 4.0.4
- ⬆️ Bump io.insert-koin:koin-compose from 4.0.2 to 4.0.4

## Documentation 📝
- :memo: Add DeepWiki link to README (#2671)
- :bookmark: Update version to 1.1.2 (#2675)

# [1.1.1] - 2025-1-30
## Bug Fixes 🐛
- :bug: Fix switch component with label text (#2310)
- :bug: Support automatic type conversion in config copying (#2321)
- :bug: Incorrect background color of contrast button (#2325)
- :bug: Fix color copy failure issue (#2341)
- :bug: The log files can sometimes be very large (#2342)
- :bug: Ensure UI updates correctly when Realm object state changes (#2348)
- :bug: Catch exceptions thrown by existFile (#2355)

## New Features ✨
- :sparkles: Add dynamic theme support (#2312)
- :sparkles: Make logo adapt to app theme (#2329)
- :sparkles: Add dynamic parameter formatting support for i18n API (#2387)
- :sparkles: Support direct access to the web URL path with i18n support (#2383)

## UI Improvements 💄
- :lipstick: Increase touch targets size according to Google Play report (#2306)
- :lipstick: Improve UI contrast ratios based on Google Play report (#2308)
- :lipstick: Add three contrast levels for theme settings (#2323)
- 💄 Adaptive status colors based on background (#2334)
- :lipstick: Adaptive notification colors based on background (#2339)
- :lipstick: Improve Counter component contrast ratio (#2344)
- :bug: Standardize preview image sizes using default screen density (#2388)

## Multiplatform & Refactor & Code Style 🔨
- :hammer: Move logo view to expect implementation in commonMain (#2331)
- :hammer: Add openTopBar parameter to PasteboardScreen (#2346)
- :hammer: Remove unused commented code (#2357)
- :hammer: Refactor RealmManagerFactory (#2360)
- :hammer: Implement dependency injection by modules (#2362)
- :hammer: Add utility method to get current timestamp (#2368)
- :hammer: Extract common signature logic in CryptographyUtils (#2370)
- :hammer: Restructure UI implementation for better multiplatform reusability (#2372)
- :hammer: Convert interface to actual/expect implementation (#2374)
- :hammer: Refactor settings UI for mobile customization (#2376)
- :hammer: Refactor AppLaunch to improve mobile usability (#2385)

## Dependencies ⬆️
- ⬆️ Bump ktor from 3.0.2 to 3.0.3
- ⬆️ Bump kotlinx-coroutines from 1.9.0 to 1.10.1
- ⬆️ Bump jna from 5.15.0 to 5.16.0
- ⬆️ Bump guava from 33.3.1-jre to 33.4.0-jre
- ⬆️ Bump compose-plugin from 1.7.1 to 1.7.3
- ⬆️ Bump webp-imageio from 0.8.0 to 0.9.0
- ⬆️ Bump koin from 4.0.0 to 4.0.1
- ⬆️ Bump logback-classic from 1.5.12 to 1.5.15
- ⬆️ Bump koin-compose from 4.0.0 to 4.0.1
- ⬆️ Bump mockk from 1.13.13 to 1.13.14
- ⬆️ Bump logback-classic from 1.5.15 to 1.5.16
- ⬆️ Bump okio from 3.9.1 to 3.10.2
- ⬆️ Bump kotlinx-serialization-json from 1.7.3 to 1.8.0
- ⬆️ Bump mockk from 1.13.14 to 1.13.16
- ⬆️ Bump koin from 4.0.1 to 4.0.2
- ⬆️ Bump koin-compose from 4.0.1 to 4.0.2

## Documentation 📝
- :bookmark: Update version to 1.1.1 (#2400)

# [1.1.0] - 2024-12-20
## Bug Fixes
- :bug: Resolve custom encryption related bugs
- :bug: Resolve incorrect deletion of folder pasteboard
- :bug: Fix bugs related to multi-file and multi-image pasteboard handling
- :bug: Fix commonMain implementation to support iOS compilation
- :bug: Avoid circular reference between fileUtils and CodecsUtils
- :bug: Fix code style issues
- :bug: Fix PasteTaskExtraInfo serialization issue on iOS
- :bug: Use class instead of object for serialization to prevent iOS crashes
- :bug: Disable encryption on error response
- :bug: Fix FileInfoTree serialization issue on iOS
- :bug: Fix storage migration error where multi-language conversion was incorrect
- :bug: Display AdaptiveTextButton correctly on Android

## New Features
- :sparkles: Implement custom encryption protocol to replace signal
- :sparkles: Add click-to-copy feature for pasteboard
- :sparkles: Add toggle switch for sound effects
- :sparkles: Support Beta AppEnv to enable mobile beta version public testing
- :sparkles: Abstract application launch states
- :sparkles: Implement RealmManagerFactory for custom database storage across platforms
- :sparkles: Add placeholder and leadingIcon to DefaultTextField

## UI Improvements
- :lipstick: Auto-scroll pasteboard list to latest item
- :lipstick: Enhance UI contrast in device and settings screen
- :lipstick: Enhance UI components visibility and theming
- :lipstick: Add padding to Counter component UI

## Multiplatform & Refactor & Code Style
- :hammer: Optimize urlBuilder related interfaces to simplify usage
- :hammer: Move ExceptionHandler to commonMain for multiplatform support
- :hammer: Remove accidentally committed debug logs
- :hammer: Refactor single file and image view
- :hammer: Migrate FileIcon to Material 3 icon
- :hammer: Move file hash algorithm to commonMain for multiplatform reuse
- :hammer: Remove unused Base64 MIME encode/decode functions
- :hammer: Move SHA256 algorithm to commonMain for multiplatform reuse
- :hammer: Replace manual path separator with Okio Path API
- :hammer: Make FileInfoTree creation multiplatform compatible
- :hammer: Replace javaClass comparison with platform-independent implementation in equals method
- :hammer: Abstract common logic into CacheManager interface
- :hammer: Base64 implementation using kotlin.io, better multiplatform equivalent implementation
- :hammer: Add Server interface for better iOS SwiftUI integration
- :hammer: Migrate state variables to StateFlow in DeviceManager
- :hammer: Optimize app sync api compatibility check
- :hammer: Refactor Data Communication Layer to Avoid Direct Realm Object Serialization
- :hammer: Make PasteServer extensible for mobile platforms
- :hammer: Implement multiplatform dateUtils for reusability
- :hammer: Convert RealmInstant utils to extension functions
- :hammer: Make launch function suspendable
- :zap: Optimize heartbeat performance by caching sync information

## Dependencies
- ⬆️ Bump coil from 3.0.0-rc02 to 3.0.2
- ⬆️ Bump org.jmdns:jmdns from 3.5.12 to 3.6.0
- ⬆️ Bump ktor from 2.3.12 to 3.0.1
- ⬆️ Bump lifecycle from 2.8.3 to 2.8.4
- ⬆️ Bump coil from 3.0.2 to 3.0.3
- ⬆️ Bump compose-plugin from 1.7.0 to 1.7.1
- ⬆️ Bump io.github.oshai:kotlin-logging from 7.0.0 to 7.0.3
- ⬆️ Bump org.jlleitschuh.gradle.ktlint from 12.1.1 to 12.1.2
- ⬆️ Bump coil from 3.0.3 to 3.0.4
- ⬆️ Bump org.jsoup:jsoup from 1.18.1 to 1.18.3
- ⬆️ Bump ktor from 3.0.1 to 3.0.2
- ⬆️ Bump compose from 1.7.5 to 1.7.6

## Documentation
- :memo: Add tech blog links to README
- :memo: Remove signal documentation
- :bookmark: Update version to 1.1.0

**Full Changelog**: https://github.com/CrossPaste/crosspaste-desktop/compare/1.0.13.1121...1.1.0.1184


# [1.0.13] - 2024-11-6
## Bug Fixes
* :bug: Initialize copywriter and notificationManager in configManager by @guiyanakuang in https://github.com/CrossPaste/crosspaste-desktop/pull/2115
* :bug: Correct image cropping and centering on Android platform by @guiyanakuang in https://github.com/CrossPaste/crosspaste-desktop/pull/2144
* :bug: Improve color format parsing and add conversion tests by @guiyanakuang in https://github.com/CrossPaste/crosspaste-desktop/pull/2153
* :bug: Actively close Realm database when exiting the app to ensure data persistence by @guiyanakuang in https://github.com/CrossPaste/crosspaste-desktop/pull/2170
* :bug: Fix regression issue with image type pasteboard display by @guiyanakuang in https://github.com/CrossPaste/crosspaste-desktop/pull/2176

## New Features
* :sparkles: Enable ThemeDetector to get current ColorScheme by @guiyanakuang in https://github.com/CrossPaste/crosspaste-desktop/pull/2107
* :sparkles: Enhance QR code generation and parsing by @guiyanakuang in https://github.com/CrossPaste/crosspaste-desktop/pull/2133
* :sparkles: Support auto-filling verification token from cache by @guiyanakuang in https://github.com/CrossPaste/crosspaste-desktop/pull/2140
* :sparkles: Add NoneTransferData to handle invalid data by @guiyanakuang in https://github.com/CrossPaste/crosspaste-desktop/pull/2147
* :sparkles: Support for color data in pasteboard by @guiyanakuang in https://github.com/CrossPaste/crosspaste-desktop/pull/2151
* :sparkles: Add support for updating color palette by @guiyanakuang in https://github.com/CrossPaste/crosspaste-desktop/pull/2155

## UI Improvements
* :lipstick: Add About item to main settings menu by @guiyanakuang in https://github.com/CrossPaste/crosspaste-desktop/pull/2105
* :art: Wrap DeviceConnectView inside Column for better layout isolation by @guiyanakuang in https://github.com/CrossPaste/crosspaste-desktop/pull/2111
* :lipstick: Define QR code scanning interface for mobile by @guiyanakuang in https://github.com/CrossPaste/crosspaste-desktop/pull/2129
* :zap: Improve pasteboard loading logic by @guiyanakuang in https://github.com/CrossPaste/crosspaste-desktop/pull/2159
* :zap: Improve search window state management by @guiyanakuang in https://github.com/CrossPaste/crosspaste-desktop/pull/2161

## Multiplatform  & Refactor & Code Style
* :hammer: Make AppTokenService multiplatform reusable by @guiyanakuang in https://github.com/CrossPaste/crosspaste-desktop/pull/2109
* :hammer: Move EndpointInfoFactory to commonMain for multiplatform reuse by @guiyanakuang in https://github.com/CrossPaste/crosspaste-desktop/pull/2113
* :hammer: Move common pasteboard consumption logic to TransferableConsumer for multiplatform reuse by @guiyanakuang in https://github.com/CrossPaste/crosspaste-desktop/pull/2117
* :hammer: Extract remote pasteboard listening service to common interface by @guiyanakuang in https://github.com/CrossPaste/crosspaste-desktop/pull/2120
* :hammer: Refactor QR code UI for multiplatform reuse by @guiyanakuang in https://github.com/CrossPaste/crosspaste-desktop/pull/2126
* :hammer: Restructure notification component for multiplatform reuse by @guiyanakuang in https://github.com/CrossPaste/crosspaste-desktop/pull/2131
* :hammer: Optimize SyncManager implementation by @guiyanakuang in https://github.com/CrossPaste/crosspaste-desktop/pull/2138
* :hammer: Extract PasteMenuService interface for multiplatform reuse by @guiyanakuang in https://github.com/CrossPaste/crosspaste-desktop/pull/2142
* :hammer: Migrate pasteboard process plugin to common module for multiplatform support by @guiyanakuang in https://github.com/CrossPaste/crosspaste-desktop/pull/2149
* :hammer: Move shared utils implementation to commonMain by @guiyanakuang in https://github.com/CrossPaste/crosspaste-desktop/pull/2157
* :hammer: Refactor search input into separate component by @guiyanakuang in https://github.com/CrossPaste/crosspaste-desktop/pull/2163
* :hammer: enhance pastetype from object to data class by @guiyanakuang in https://github.com/CrossPaste/crosspaste-desktop/pull/2172

## Dependencies
* ⬆️ Bump ch.qos.logback:logback-classic from 1.5.8 to 1.5.12 by @dependabot in https://github.com/CrossPaste/crosspaste-desktop/pull/2136
* ⬆️ Bump dev.hydraulic.conveyor from 1.11 to 1.12 by @dependabot in https://github.com/CrossPaste/crosspaste-desktop/pull/2135
* ⬆️ Bump coil from 3.0.0-rc01 to 3.0.0-rc02 by @dependabot in https://github.com/CrossPaste/crosspaste-desktop/pull/2164
* ⬆️ Bump androidx.compose.material3:material3 from 1.3.0 to 1.3.1 by @dependabot in https://github.com/CrossPaste/crosspaste-desktop/pull/2167
* ⬆️ Bump compose from 1.7.4 to 1.7.5 by @dependabot in https://github.com/CrossPaste/crosspaste-desktop/pull/2166

## Documentation
* :memo: Update changelog / download to 1.0.12 by @guiyanakuang in https://github.com/CrossPaste/crosspaste-desktop/pull/2122
* :memo: Add QR code scanner and token SVG support by @guiyanakuang in https://github.com/CrossPaste/crosspaste-desktop/pull/2124
* 📝 Update `SortPlugin.kt` reference by @emmanuel-ferdman in https://github.com/CrossPaste/crosspaste-desktop/pull/2150
* :memo: Update version to 1.0.13 by @guiyanakuang in https://github.com/CrossPaste/crosspaste-desktop/pull/2174

## New Contributors
* @emmanuel-ferdman made their first contribution in https://github.com/CrossPaste/crosspaste-desktop/pull/2150

**Full Changelog**: https://github.com/CrossPaste/crosspaste-desktop/compare/1.0.12.1084...1.0.13.1121

# [1.0.12] - 2024-10-19
## Bug Fixes

* :bug: Fix issue where clicking on the main interface causes hidden windows to be hidden by @guiyanakuang in https://github.com/CrossPaste/crosspaste-desktop/pull/2035
* :bug: Fix simple code smells by @guiyanakuang in https://github.com/CrossPaste/crosspaste-desktop/pull/2066
* :bug: Fix the path for referencing resource files in conveyor by @guiyanakuang in https://github.com/CrossPaste/crosspaste-desktop/pull/2088
* :bug: Fix icon path error issue on Linux by @guiyanakuang in https://github.com/CrossPaste/crosspaste-desktop/pull/2102


## New Features

* :sparkles: Enhance desktop plugin class implementation by @guiyanakuang in https://github.com/CrossPaste/crosspaste-desktop/pull/2044
* :sparkles: Store only image type when copying browser images, remove HTML type by @guiyanakuang in https://github.com/CrossPaste/crosspaste-desktop/pull/2046
* :sparkles: Add separate right-click menu for pasteboard by @guiyanakuang in https://github.com/CrossPaste/crosspaste-desktop/pull/2047
* :sparkles: Add support for RTF format pasteboard data by @guiyanakuang in https://github.com/CrossPaste/crosspaste-desktop/pull/2052

## UI Improvements

* :zap: Enhance Transparency Checkerboard for improved image background by @guiyanakuang in https://github.com/CrossPaste/crosspaste-desktop/pull/2057
* :zap: Improve device sync logic by @guiyanakuang in https://github.com/CrossPaste/crosspaste-desktop/pull/2090
* :lipstick: Implement backspace functionality in DeviceVerifyView token input by @guiyanakuang in https://github.com/CrossPaste/crosspaste-desktop/pull/2092

## Refactor & Code Style

* :hammer: Fix issues found by code inspection by @guiyanakuang in https://github.com/CrossPaste/crosspaste-desktop/pull/2031
* :hammer: Modify HtmlRenderingService API to support asynchronous HTML rendering by @guiyanakuang in https://github.com/CrossPaste/crosspaste-desktop/pull/2037
* :art: Modify plugin interface to add pasteboard source parameter by @guiyanakuang in https://github.com/CrossPaste/crosspaste-desktop/pull/2042
* :hammer: Improve screen routing implementation by @guiyanakuang in https://github.com/CrossPaste/crosspaste-desktop/pull/2049
* :hammer: Move getPasteTitle api to PasteItem interface by @guiyanakuang in https://github.com/CrossPaste/crosspaste-desktop/pull/2055
* :memo: Implement ReadWriteConfig to decouple services from specific configurations, facilitating the creation of unit tests by @guiyanakuang in https://github.com/CrossPaste/crosspaste-desktop/pull/2074
* :fire: Remove theme listener interface as it's unnecessary; Compose UI will automatically recompose based on state by @guiyanakuang in https://github.com/CrossPaste/crosspaste-desktop/pull/2076
* :hammer: Implement DialogService in commonMain for multi-platform reuse by @guiyanakuang in https://github.com/CrossPaste/crosspaste-desktop/pull/2078
* :hammer: Refactor theme implementation by @guiyanakuang in https://github.com/CrossPaste/crosspaste-desktop/pull/2080
* :hammer: Improve UI Reusability and multiplatform Compatibility by @guiyanakuang in https://github.com/CrossPaste/crosspaste-desktop/pull/2082
* :hammer: Optimize code style by @guiyanakuang in https://github.com/CrossPaste/crosspaste-desktop/pull/2084

## Dependencies

* ⬆️ Bump realm from 2.3.0 to 3.0.0 by @dependabot in https://github.com/CrossPaste/crosspaste-desktop/pull/2028
* ⬆️ Bump compose from 1.7.2 to 1.7.3 by @dependabot in https://github.com/CrossPaste/crosspaste-desktop/pull/2026
* ⬆️ Bump kotlin from 2.0.20 to 2.0.21 by @dependabot in https://github.com/CrossPaste/crosspaste-desktop/pull/2061
* ⬆️ Bump imageio from 3.11.0 to 3.12.0 by @dependabot in https://github.com/CrossPaste/crosspaste-desktop/pull/2060
* ⬆️ Bump coil from 3.0.0-alpha10 to 3.0.0-rc01 by @dependabot in https://github.com/CrossPaste/crosspaste-desktop/pull/2058
* :arrow_up: Bump compose plugin to 1.7.0-rc01 by @guiyanakuang in https://github.com/CrossPaste/crosspaste-desktop/pull/2068
* ⬆️ Bump compose from 1.7.3 to 1.7.4 by @dependabot in https://github.com/CrossPaste/crosspaste-desktop/pull/2096
* ⬆️ Bump lifecycle from 2.8.2 to 2.8.3 by @dependabot in https://github.com/CrossPaste/crosspaste-desktop/pull/2095
* ⬆️ Bump io.mockk:mockk from 1.13.12 to 1.13.13 by @dependabot in https://github.com/CrossPaste/crosspaste-desktop/pull/2094
* :arrow_up: Bump compose-plugin from 1.7.0-rc01 to 1.7.0 by @guiyanakuang in https://github.com/CrossPaste/crosspaste-desktop/pull/2098

## Documentation

* :memo: Update pasteboard concept, add RTF type support by @guiyanakuang in https://github.com/CrossPaste/crosspaste-desktop/pull/2054
* :memo: Update the current roadmap by @guiyanakuang in https://github.com/CrossPaste/crosspaste-desktop/pull/2072

**Full Changelog**: https://github.com/CrossPaste/crosspaste-desktop/compare/1.0.11.1046...1.0.12.1084

# [1.0.11] - 2024-10-04

## Bug Fixes
* :bug: Improve network interface handling and error management by @guiyanakuang in https://github.com/CrossPaste/crosspaste-desktop/pull/1956
* :bug: Fix repeated reading of ImageBitmap by @guiyanakuang in https://github.com/CrossPaste/crosspaste-desktop/pull/1965
* :bug: Fix issue with appSourceFetcher retrieving application icons by @guiyanakuang in https://github.com/CrossPaste/crosspaste-desktop/pull/1974
* :bug: Fix alignment of HTML preview pasteboard by @guiyanakuang in https://github.com/CrossPaste/crosspaste-desktop/pull/1976
* :bug: Fix UI issue where the bottom bar was not displayed in the search window by @guiyanakuang in https://github.com/CrossPaste/crosspaste-desktop/pull/2014
* :bug: Fix issue where shortcut key order affects simulated paste by @guiyanakuang in https://github.com/CrossPaste/crosspaste-desktop/pull/2025

## New Features
* :sparkles: implement voice prompt functionality by @guiyanakuang in https://github.com/CrossPaste/crosspaste-desktop/pull/1945
* :sparkles: Add support for debug mode by @guiyanakuang in https://github.com/CrossPaste/crosspaste-desktop/pull/1954
* :sparkles: Add support for unassigned shortcuts by @guiyanakuang in https://github.com/CrossPaste/crosspaste-desktop/pull/1961
* :sparkles: Support cross-application paste by double-clicking pasteboard item in main window by @guiyanakuang in https://github.com/CrossPaste/crosspaste-desktop/pull/2021

## Performance & UI Improvements
* :heavy_plus_sign: Support lifecycle for better control of memory and resource usage by @guiyanakuang in https://github.com/CrossPaste/crosspaste-desktop/pull/1959
* :zap: Use Coil for asynchronous image loading by @guiyanakuang in https://github.com/CrossPaste/crosspaste-desktop/pull/1967
* :lipstick: Unified Divider Style by @guiyanakuang in https://github.com/CrossPaste/crosspaste-desktop/pull/2009
* :lipstick: Add proper padding when displaying text due to HTML rendering failure by @guiyanakuang in https://github.com/CrossPaste/crosspaste-desktop/pull/2020

## Refactor & Code Style
* :hammer: Refactor routing impl for early returns and reduced nesting by @guiyanakuang in https://github.com/CrossPaste/crosspaste-desktop/pull/1953
* :hammer: Refactor pasteboard preview UI using ViewModel by @guiyanakuang in https://github.com/CrossPaste/crosspaste-desktop/pull/1963
* :hammer: Refactor application window implementation by @guiyanakuang in https://github.com/CrossPaste/crosspaste-desktop/pull/1972
* :hammer: Replace desktop click methods with pointerInput for multiplatform support by @guiyanakuang in https://github.com/CrossPaste/crosspaste-desktop/pull/1980
* :hammer: Refactor PlatformContext retrieval for multi-platform compatibility by @guiyanakuang in https://github.com/CrossPaste/crosspaste-desktop/pull/1982
* :hammer: Use official method to load drawable resources for multiplatform reuse by @guiyanakuang in https://github.com/CrossPaste/crosspaste-desktop/pull/1984
* :hammer: Use expect/actual for FileSystem to support multiple platforms by @guiyanakuang in https://github.com/CrossPaste/crosspaste-desktop/pull/1986
* :hammer: Fix UI multiplatform reuse issues by @guiyanakuang in https://github.com/CrossPaste/crosspaste-desktop/pull/1988
* :hammer: Use official method to load fonts for multiplatform reuse by @guiyanakuang in https://github.com/CrossPaste/crosspaste-desktop/pull/1990
* :hammer: Refactor Coil implementation and introduce dependency injection by @guiyanakuang in https://github.com/CrossPaste/crosspaste-desktop/pull/1992
* :hammer: Refactor UI components, move desktop-related component UI abstractions to desktopMain by @guiyanakuang in https://github.com/CrossPaste/crosspaste-desktop/pull/1994
* :hammer: Use dependency injection to obtain LocaleUtils, facilitating multiplatform implementation by @guiyanakuang in https://github.com/CrossPaste/crosspaste-desktop/pull/1996
* :hammer: Abstract PasteShimmer to allow independent impl of shimmer effects on different platforms by @guiyanakuang in https://github.com/CrossPaste/crosspaste-desktop/pull/1998
* :hammer: Provide AppEnvUtils to allow multiple platforms to impl equivalent functionality by @guiyanakuang in https://github.com/CrossPaste/crosspaste-desktop/pull/2000
* :hammer: Replace unnecessary BoxWithConstraints with Box by @guiyanakuang in https://github.com/CrossPaste/crosspaste-desktop/pull/2002
* :hammer: Refactor commonMain for multiplatform compatibility by @guiyanakuang in https://github.com/CrossPaste/crosspaste-desktop/pull/2011
* :hammer: Abstract AbstractFaviconLoader and AbstractThumbnailLoader to allow maximum reuse across multiple platforms by @guiyanakuang in https://github.com/CrossPaste/crosspaste-desktop/pull/2016
* :hammer: Use direct import instead of referencing through package by @guiyanakuang in https://github.com/CrossPaste/crosspaste-desktop/pull/2018
* :hammer: Rename ChromeService to HtmlRenderingService by @guiyanakuang in https://github.com/CrossPaste/crosspaste-desktop/pull/2023

## Dependencies
* ⬆️ Bump org.jetbrains.kotlinx:kotlinx-serialization-json from 1.7.2 to 1.7.3 by @dependabot in https://github.com/CrossPaste/crosspaste-desktop/pull/1950
* ⬆️ Bump io.insert-koin:koin-compose from 4.0.0-RC2 to 4.0.0 by @dependabot in https://github.com/CrossPaste/crosspaste-desktop/pull/1949
* ⬆️ Bump io.insert-koin:koin-core from 3.5.6 to 4.0.0 by @dependabot in https://github.com/CrossPaste/crosspaste-desktop/pull/1946
* ⬆️ Bump compose from 1.7.1 to 1.7.2 by @dependabot in https://github.com/CrossPaste/crosspaste-desktop/pull/1948
* ⬆️ Bump org.seleniumhq.selenium:selenium-manager from 4.24.0 to 4.25.0 by @dependabot in https://github.com/CrossPaste/crosspaste-desktop/pull/1947
* ⬆️ Bump com.google.guava:guava from 33.3.0-jre to 33.3.1-jre by @dependabot in https://github.com/CrossPaste/crosspaste-desktop/pull/1969

## Build System
* :construction_worker: Remove the task for extracting selenium-manager, as it is no longer used by @guiyanakuang in https://github.com/CrossPaste/crosspaste-desktop/pull/1978

## Documentation
* :memo: Update changelog / download to 1.0.10 by @guiyanakuang in https://github.com/CrossPaste/crosspaste-desktop/pull/1939
* :memo: Commit Message Guide with Emojis by @guiyanakuang in https://github.com/CrossPaste/crosspaste-desktop/pull/1944

## Testing
* :white_check_mark: Add unit tests for NetworkUtils by @guiyanakuang in https://github.com/CrossPaste/crosspaste-desktop/pull/1941

**Full Changelog**: https://github.com/CrossPaste/crosspaste-desktop/compare/1.0.10.1001...1.0.11.1037
**Full Changelog**: https://github.com/CrossPaste/crosspaste-desktop/compare/1.0.11.1037...1.0.11.1046

# [1.0.10] - 2024-09-21

## Bug Fixes
* 🐛 [Mac] Skip listening to initial pasteboard change by @guiyanakuang in https://github.com/CrossPaste/crosspaste-desktop/pull/1905
* 🐛 Use chrome-headless-shell for HTML rendering without window creation by @guiyanakuang in https://github.com/CrossPaste/crosspaste-desktop/pull/1925
* 🐛 Fix bug in verifying if proxy is working by @guiyanakuang in https://github.com/CrossPaste/crosspaste-desktop/pull/1929
* 🐛 Fix the issue where the hover effect of the search button on the main UI is affected by TokenView by @guiyanakuang in https://github.com/CrossPaste/crosspaste-desktop/pull/1931
* 🐛 Relax restrictions on private IP addresses by @guiyanakuang in https://github.com/CrossPaste/crosspaste-desktop/pull/1927

## New Features
* ✨ Integrate MurmurHash3 source code and add StreamingMurmurHash3 by @guiyanakuang in https://github.com/CrossPaste/crosspaste-desktop/pull/1901
* ✨ Implement native macOS API for thumbnail generation by @guiyanakuang in https://github.com/CrossPaste/crosspaste-desktop/pull/1907

## UI Improvements
* 💄 Upgrade UI to Material 3 for reuse on mobile platforms by @guiyanakuang in https://github.com/CrossPaste/crosspaste-desktop/pull/1915

## Refactor & Code Style
* 🔨 Merge FileExtUtils into FileUtils by @guiyanakuang in https://github.com/CrossPaste/crosspaste-desktop/pull/1889
* 🔨 Constants in PasteTypePlugin are now uniformly recorded within the default companion object by @guiyanakuang in https://github.com/CrossPaste/crosspaste-desktop/pull/1891
* 🔨 Fix typos throughout the project by @guiyanakuang in https://github.com/CrossPaste/crosspaste-desktop/pull/1897
* 🔨 Refactor ImageWriter into a generic interface for reuse on mobile platforms by @guiyanakuang in https://github.com/CrossPaste/crosspaste-desktop/pull/1899
* 🔨 Refactor toByteArray to be platform-independent in commonMain by @guiyanakuang in https://github.com/CrossPaste/crosspaste-desktop/pull/1909
* 🔨 Allow null ext in createRandomFileName for multi-platform reuse by @guiyanakuang in https://github.com/CrossPaste/crosspaste-desktop/pull/1914
* 🔨 Implement expect/actual pattern for main UI screens by @guiyanakuang in https://github.com/CrossPaste/crosspaste-desktop/pull/1921
* 🔨 Optimize NetUtils and DeviceUtils by @guiyanakuang in https://github.com/CrossPaste/crosspaste-desktop/pull/1933
* 🔨 Move font file to the resource folder in desktopMain by @guiyanakuang in https://github.com/CrossPaste/crosspaste-desktop/pull/1936

## Dependencies
* ⬆️ Bump jna from 5.14.0 to 5.15.0 by @dependabot in https://github.com/CrossPaste/crosspaste-desktop/pull/1893
* ⬆️ Bump compose from 1.7.0 to 1.7.1 by @dependabot in https://github.com/CrossPaste/crosspaste-desktop/pull/1892
* ⬆️ Bump com.squareup.okio:okio from 3.9.0 to 3.9.1 by @dependabot in https://github.com/CrossPaste/crosspaste-desktop/pull/1894
* ⬆️ Bump kotlinx-coroutines from 1.8.1 to 1.9.0 by @dependabot in https://github.com/CrossPaste/crosspaste-desktop/pull/1895
* ⬆️ Bump realm from 2.1.0 to 2.3.0 by @dependabot in https://github.com/CrossPaste/crosspaste-desktop/pull/1902
* ⬆️ Bump kotlin from 2.0.10 to 2.0.20 by @dependabot in https://github.com/CrossPaste/crosspaste-desktop/pull/1728

## Build System
* 👷 Enable expect/actual classes in JVM target with "-Xexpect-actual-classes" flag by @guiyanakuang in https://github.com/CrossPaste/crosspaste-desktop/pull/1911

**Full Changelog**: https://github.com/CrossPaste/crosspaste-desktop/compare/1.0.9.974...1.0.10.1001

# [1.0.9] - 2024-09-13

Extensive refactoring has been done to make commonMain reusable across multiple platforms, preparing for mobile implementation

## Bug Fixes
* 🐛 [Win] Fix mouse cursor displacement to bottom-right when invoking search window by @guiyanakuang in https://github.com/CrossPaste/crosspaste-desktop/pull/1816
* 🐛 Fix bug where app reads pasteboard on first launch to get CrossPaste source by @guiyanakuang in https://github.com/CrossPaste/crosspaste-desktop/pull/1875
* 🐛 Failure to copy images exceeding backup file threshold by @guiyanakuang in https://github.com/CrossPaste/crosspaste-desktop/pull/1879

## New Features
* ✨ Add a switch to control whether to read pasteboard content set before application startup by @guiyanakuang in https://github.com/CrossPaste/crosspaste-desktop/pull/1877

## UI Improvements
* 💄 Hide tray immediately on application exit by @guiyanakuang in https://github.com/CrossPaste/crosspaste-desktop/pull/1810

## Optimizations
* ⚡ Prioritize matching pasteboard when searching and pasting by @guiyanakuang in https://github.com/CrossPaste/crosspaste-desktop/pull/1815

## Refactor & Code Style
* 🔨 Refactor atomic operations for cross-platform reuse by @guiyanakuang in https://github.com/CrossPaste/crosspaste-desktop/pull/1796
* 🔨 Remove endpoint package and refactor code by @guiyanakuang in https://github.com/CrossPaste/crosspaste-desktop/pull/1798
* 🔨 Internationalize project by converting Chinese comments to English by @guiyanakuang in https://github.com/CrossPaste/crosspaste-desktop/pull/1800
* 🔨 Move Realm storage initialization to commonMain by @guiyanakuang in https://github.com/CrossPaste/crosspaste-desktop/pull/1802
* 🔨 refactor UserDataPathProvider: use cross-platform APIs by @guiyanakuang in https://github.com/CrossPaste/crosspaste-desktop/pull/1804
* 🔨 standardize logger creation within respective classes by @guiyanakuang in https://github.com/CrossPaste/crosspaste-desktop/pull/1806

<details>
<summary>Click to expand detailed Refactor & Code Style notes</summary>

* 🔨 Refactor TxtRecordUtils for multi-platform support by @guiyanakuang in https://github.com/CrossPaste/crosspaste-desktop/pull/1809
* 🔨 Refactor Ktor plugin and client code for multi-platform support by @guiyanakuang in https://github.com/CrossPaste/crosspaste-desktop/pull/1812
* 🔨 Extract cross-platform logic into BaseSyncRouting for iOS/Android reuse by @guiyanakuang in https://github.com/CrossPaste/crosspaste-desktop/pull/1818
* 🔨 Move PasteRouting and PullRouting to commonMain for code reuse by @guiyanakuang in https://github.com/CrossPaste/crosspaste-desktop/pull/1820
* 🔨 Move AppPathProvider interface to desktopMain for desktop-specific impl by @guiyanakuang in https://github.com/CrossPaste/crosspaste-desktop/pull/1824
* 🔨 Merge os package into platform package and rename currentPlatform to getPlatform by @guiyanakuang in https://github.com/CrossPaste/crosspaste-desktop/pull/1826
* 🔨 Refactor file persistence to multiplatform impl by @guiyanakuang in https://github.com/CrossPaste/crosspaste-desktop/pull/1828
* 🔨 Refactor DesktopDeviceManager to DeviceManager for multi-platform support by @guiyanakuang in https://github.com/CrossPaste/crosspaste-desktop/pull/1832
* 🔨 Refactor SyncManager and SyncHandler for multi-platform reuse in iOS and Android by @guiyanakuang in https://github.com/CrossPaste/crosspaste-desktop/pull/1834
* 🔨 Refactor DesktopPasteServer for multi-platform support by @guiyanakuang in https://github.com/CrossPaste/crosspaste-desktop/pull/1836
* 🔨 Refactor QR code generation to support multiplatform by @guiyanakuang in https://github.com/CrossPaste/crosspaste-desktop/pull/1847
* 🔨 Refactor task module for multi-platform impl by @guiyanakuang in https://github.com/CrossPaste/crosspaste-desktop/pull/1849
* 🔨 Convert TaskUtils to multiplatform impl by @guiyanakuang in https://github.com/CrossPaste/crosspaste-desktop/pull/1851
* 🔨 Move Realm query impl to commonMain by @guiyanakuang in https://github.com/CrossPaste/crosspaste-desktop/pull/1853
* 🔨 Migrate clientApi impl to commonMain by @guiyanakuang in https://github.com/CrossPaste/crosspaste-desktop/pull/1855
* 🔨 Migrate SyncInfoFactory to commonMain by @guiyanakuang in https://github.com/CrossPaste/crosspaste-desktop/pull/1859
* 🔨 Adopt Ktor's multiplatform concurrent map by @guiyanakuang in https://github.com/CrossPaste/crosspaste-desktop/pull/1857
* 🔨 Abstract AbstractFileExtImageLoader for multi-platform reuse by @guiyanakuang in https://github.com/CrossPaste/crosspaste-desktop/pull/1864
* 🔨 Categorize and sort dependency injection items by @guiyanakuang in https://github.com/CrossPaste/crosspaste-desktop/pull/1865
* 🔨 Rename PlatformUtils to DispatcherUtils for better accuracy by @guiyanakuang in https://github.com/CrossPaste/crosspaste-desktop/pull/1867
* 🔨 Refactor DesktopPasteSyncProcessManager to commonMain for multi-platform reuse by @guiyanakuang in https://github.com/CrossPaste/crosspaste-desktop/pull/1869
* 🔨 Optimize lock usage and replace AtomicLock by @guiyanakuang in https://github.com/CrossPaste/crosspaste-desktop/pull/1871
* 🔨 Refactor Compose dependency injection to use official Koin methods by @guiyanakuang in https://github.com/CrossPaste/crosspaste-desktop/pull/1873
* 🔨 Remove unused implementation of PasteResourceLoader by @guiyanakuang in https://github.com/CrossPaste/crosspaste-desktop/pull/1882
* 🔨 Standardize using 'get' method to obtain utils classes by @guiyanakuang in https://github.com/CrossPaste/crosspaste-desktop/pull/1884
* 🔨 Merge two interfaces of NotificationManager, no need for repetition by @guiyanakuang in https://github.com/CrossPaste/crosspaste-desktop/pull/1886
</details>

## Documentation
* 📝 Add Frequently Asked Questions (FAQ) document by @guiyanakuang in https://github.com/CrossPaste/crosspaste-desktop/pull/1794

## Dependencies
* ⬆️ Bump ch.qos.logback:logback-classic from 1.5.7 to 1.5.8 by @dependabot in https://github.com/CrossPaste/crosspaste-desktop/pull/1840
* ⬆️ Bump dev.hydraulic.conveyor from 1.10 to 1.11 by @dependabot in https://github.com/CrossPaste/crosspaste-desktop/pull/1839
* ⬆️ Bump compose from 1.6.8 to 1.7.0 by @dependabot in https://github.com/CrossPaste/crosspaste-desktop/pull/1837

## New Contributors
* @sunxiang0918 Thanks for providing multiple detailed bug reproduction processes

**Full Changelog**: https://github.com/CrossPaste/crosspaste-desktop/compare/1.0.8.925...1.0.9.974

# [1.0.8] - 2024-09-04

## Bug Fixes
* 🐛 Resolve bug in application version check by @guiyanakuang in [#1726](https://github.com/CrossPaste/crosspaste-desktop/pull/1726)
* 🐛 Fix hash encoding and refactor it to implement as cross-platform code by @guiyanakuang in [#1754](https://github.com/CrossPaste/crosspaste-desktop/pull/1754)
* 🐛 Application crash on system tray icon click sigsegv error by @guiyanakuang in [#1756](https://github.com/CrossPaste/crosspaste-desktop/pull/1756)
* 🐛 [Linux] Fix main window display position by @guiyanakuang in [#1758](https://github.com/CrossPaste/crosspaste-desktop/pull/1758)
* 🐛 Use IPv4 and disable IPv6 addresses by @guiyanakuang in [#1772](https://github.com/CrossPaste/crosspaste-desktop/pull/1772)
* 🐛 Fix pasteboard sync bugs by @guiyanakuang in [#1773](https://github.com/CrossPaste/crosspaste-desktop/pull/1773)
* 🐛 Update trusted device info based on listeners by @guiyanakuang in [#1785](https://github.com/CrossPaste/crosspaste-desktop/pull/1785)
* 🐛 Set ChromeServiceModule files as executable before execution by @guiyanakuang in [#1789](https://github.com/CrossPaste/crosspaste-desktop/pull/1789)

## New Features
* ✨ Add manual IP and port input for connection by @guiyanakuang in [#1731](https://github.com/CrossPaste/crosspaste-desktop/pull/1731)
* ✨ Support direct modification of text pasteboard content by @guiyanakuang in [#1732](https://github.com/CrossPaste/crosspaste-desktop/pull/1732)
* ✨ Add shortcut key for pasting primary type by @guiyanakuang in [#1752](https://github.com/CrossPaste/crosspaste-desktop/pull/1752)
* ✨ Detect and notify API compatibility when different client versions connect by @guiyanakuang in [#1769](https://github.com/CrossPaste/crosspaste-desktop/pull/1769)
* ✨ Support active refreshing of device connections by @guiyanakuang in [#1777](https://github.com/CrossPaste/crosspaste-desktop/pull/1777)

## UI Improvements
* 💄 Enhance device connection refresh interaction by @guiyanakuang in [#1783](https://github.com/CrossPaste/crosspaste-desktop/pull/1783)

## Optimizations
* ⚡ Replace MD5 with Murmur3 128-bit hash for improved large file performance by @guiyanakuang in [#1745](https://github.com/CrossPaste/crosspaste-desktop/pull/1745)

## Refactor & Code Style
* 🔨 Move logic for binding desktop UI in AppWindowManager from commonMain to desktopMain by @guiyanakuang in [#1735](https://github.com/CrossPaste/crosspaste-desktop/pull/1735)
* 🔨 Move cross platform utility methods to commonmain by @guiyanakuang in [#1739](https://github.com/CrossPaste/crosspaste-desktop/pull/1739)
* 🔨 Refactor file operations using okio for cross-platform compatibility and add desktop unit tests by @guiyanakuang in [#1743](https://github.com/CrossPaste/crosspaste-desktop/pull/1743)
* 🔨 Move PasteRealm from desktopMain to commonMain by @guiyanakuang in [#1748](https://github.com/CrossPaste/crosspaste-desktop/pull/1748)
* ✏️ Correct the spelling mistakes in the list of methods. by @sunxiang0918 in [#1737](https://github.com/CrossPaste/crosspaste-desktop/pull/1737)

## Documentation
* 📝 Remove description of first-time chromeDriver / chrome-headless-shell download from README by @guiyanakuang in [#1760](https://github.com/CrossPaste/crosspaste-desktop/pull/1760)
* 📝 Move doc to separate directory to reduce clutter in main source folder by @guiyanakuang in [#1775](https://github.com/CrossPaste/crosspaste-desktop/pull/1775)

## Dependencies
* ⬆️ Bump org.jetbrains.kotlinx:kotlinx-datetime from 0.6.0 to 0.6.1 by @dependabot in [#1727](https://github.com/CrossPaste/crosspaste-desktop/pull/1727)
* ⬆️ Bump org.jmdns:jmdns from 3.5.11 to 3.5.12 by @dependabot in [#1729](https://github.com/CrossPaste/crosspaste-desktop/pull/1729)
* ⬆️ Bump org.seleniumhq.selenium:selenium-manager from 4.23.1 to 4.24.0 by @dependabot in [#1767](https://github.com/CrossPaste/crosspaste-desktop/pull/1767)
* ⬆️ Bump org.jetbrains.kotlinx:kotlinx-serialization-json from 1.7.1 to 1.7.2 by @dependabot in [#1766](https://github.com/CrossPaste/crosspaste-desktop/pull/1766)
* ⬆️ Bump org.yaml:snakeyaml from 2.2 to 2.3 by @dependabot in [#1768](https://github.com/CrossPaste/crosspaste-desktop/pull/1768)

## New Contributors
* @sunxiang0918 made their first contribution in [#1737](https://github.com/CrossPaste/crosspaste-desktop/pull/1737)

**Full Changelog**: https://github.com/CrossPaste/crosspaste-desktop/compare/1.0.7.891...1.0.8.925

# [1.0.7] - 2024-08-23

**📦 Reduced installation package size by 50% through optimizations**

## Bug Fixes
* 🐛 Fix pasteboard listener and sync encryption UI refresh by @guiyanakuang in [#1679](https://github.com/CrossPaste/crosspaste-desktop/pull/1679)
* 🐛 Fix DesktopSyncManager exit exception issue by @guiyanakuang in [#1683](https://github.com/CrossPaste/crosspaste-desktop/pull/1683)
* 🐛 Prevent Dev/Test version from modifying boot startup flag by @guiyanakuang in [#1684](https://github.com/CrossPaste/crosspaste-desktop/pull/1684)
* 🐛 Reimplemented opening file copies in temporary directory by @guiyanakuang in [#1685](https://github.com/CrossPaste/crosspaste-desktop/pull/1685)
* 🐛 Correctly detect user proxy settings by @guiyanakuang in [#1703](https://github.com/CrossPaste/crosspaste-desktop/pull/1703)
* 🐛 Add missing listener for backtick (`) key by @guiyanakuang in [#1704](https://github.com/CrossPaste/crosspaste-desktop/pull/1704)
* 🐛 Accurately obtain local ip address by @guiyanakuang in [#1705](https://github.com/CrossPaste/crosspaste-desktop/pull/1705)

## New Features
* ✨ Implement automatic UI display on first app launch by @guiyanakuang in [#1680](https://github.com/CrossPaste/crosspaste-desktop/pull/1680)
* 💄 Add support for displaying creation time in pasteboard details by @guiyanakuang in [#1691](https://github.com/CrossPaste/crosspaste-desktop/pull/1691)
* Add Ctrl+N and Ctrl+P shortcuts to search window by @Blushyes in [#1706](https://github.com/CrossPaste/crosspaste-desktop/pull/1706)

## UI Improvements
* 💄 Token and QR code refresh to add progress bar by @guiyanakuang in [#1688](https://github.com/CrossPaste/crosspaste-desktop/pull/1688)

## Documentation
* 📝 Optimize feature descriptions in README by @guiyanakuang in [#1678](https://github.com/CrossPaste/crosspaste-desktop/pull/1678)
* 📝 Create project roadmap document by @guiyanakuang in [#1687](https://github.com/CrossPaste/crosspaste-desktop/pull/1687)
* 📝 Add badges for technology stack and project information by @guiyanakuang in [#1690](https://github.com/CrossPaste/crosspaste-desktop/pull/1690)
* 📝 Add official website link to README by @guiyanakuang in [#1700](https://github.com/CrossPaste/crosspaste-desktop/pull/1700)

## Dependencies
* ⬆️ Bump selenium from 4.23.0 to 4.23.1 by @dependabot in [#1669](https://github.com/CrossPaste/crosspaste-desktop/pull/1669)
* ⬆️ Bump ch.qos.logback:logback-classic from 1.5.6 to 1.5.7 by @dependabot in [#1710](https://github.com/CrossPaste/crosspaste-desktop/pull/1710)
* ⬆️ Bump com.valentinilk.shimmer:compose-shimmer from 1.3.0 to 1.3.1 by @dependabot in [#1709](https://github.com/CrossPaste/crosspaste-desktop/pull/1709)
* ⬆️ Bump com.google.guava:guava from 33.2.1-jre to 33.3.0-jre by @dependabot in [#1708](https://github.com/CrossPaste/crosspaste-desktop/pull/1708)

## Optimizations
* 🚀 Optimize package size: Avoid bundling chrome-driver and chrome-headless-shell by @guiyanakuang in [#1698](https://github.com/CrossPaste/crosspaste-desktop/pull/1698)
* 📈 [win] Prevent exceptions from URL checks printing by @guiyanakuang in [#1722](https://github.com/CrossPaste/crosspaste-desktop/pull/1722)

## New Contributors
* @Blushyes made their first contribution in [#1706](https://github.com/CrossPaste/crosspaste-desktop/pull/1706)

**Full Changelog**: https://github.com/CrossPaste/crosspaste-desktop/compare/1.0.6.862...1.0.7.891

# [1.0.6] - 2024-08-11

## Bug Fixes
* 🐛 Resolve delayed PrevAppName refresh in search UI by @guiyanakuang in [#1649](https://github.com/CrossPaste/crosspaste-desktop/pull/1649)
* 🐛 Correct previous app info retrieval by @guiyanakuang in [#1651](https://github.com/CrossPaste/crosspaste-desktop/pull/1651)
* :bug: Fix check-metadata-url address for version checking by @guiyanakuang in https://github.com/CrossPaste/crosspaste-desktop/pull/1667

## New Features
* ✨ Add default configuration to limit auto-backup file size by @guiyanakuang in [#1629](https://github.com/CrossPaste/crosspaste-desktop/pull/1629)
* ✨ Add default configuration to limit file size for auto-sync across devices by @guiyanakuang in [#1630](https://github.com/CrossPaste/crosspaste-desktop/pull/1630)
* ✨ Support customizable pasteboard storage path by @guiyanakuang in [#1633](https://github.com/CrossPaste/crosspaste-desktop/pull/1633)
* ✨ Add shortcut for pasting plain text by @guiyanakuang in [#1640](https://github.com/CrossPaste/crosspaste-desktop/pull/1640)
* ✨ Add double-click quick copy feature by @guiyanakuang in [#1642](https://github.com/CrossPaste/crosspaste-desktop/pull/1642)
* ✨ Add one-click clear clipboard feature excluding favorites by @guiyanakuang in [#1643](https://github.com/CrossPaste/crosspaste-desktop/pull/1643)

## UI Improvements
* 💄 Add UI configuration to modify maxBackupFileSize, enabledSyncFileSizeLimit, and maxSyncFileSize by @guiyanakuang in [#1639](https://github.com/CrossPaste/crosspaste-desktop/pull/1639)
* 💄 Resolve text overflow in DialogButtonsView buttons by @guiyanakuang in [#1652](https://github.com/CrossPaste/crosspaste-desktop/pull/1652)

## Dependencies
* ⬆️ Bump org.jmdns:jmdns from 3.5.9 to 3.5.11 by @dependabot in [#1636](https://github.com/CrossPaste/crosspaste-desktop/pull/1636)
* ⬆️ Bump kotlin from 2.0.0 to 2.0.10 by @dependabot in [#1634](https://github.com/CrossPaste/crosspaste-desktop/pull/1634)

## Refactoring and Code Quality
* ♻️ Abstract SettingItemView component by @guiyanakuang in [#1653](https://github.com/CrossPaste/crosspaste-desktop/pull/1653)
* ✅ Rename test classes to follow naming conventions by @guiyanakuang in [#1659](https://github.com/CrossPaste/crosspaste-desktop/pull/1659)
* 🔨 Decouple dependency injection from koinApplication by @guiyanakuang in [#1661](https://github.com/CrossPaste/crosspaste-desktop/pull/1661)


**Full Changelog**: https://github.com/CrossPaste/crosspaste-desktop/compare/1.0.5.838...1.0.6.862


# [1.0.5] - 2024-08-05

### Bug Fixes
* 🐛 [Microsoft Store] Fix bug preventing control over startup settings for apps installed from Microsoft Store by @guiyanakuang in [#1604](https://github.com/CrossPaste/crosspaste-desktop/pull/1604)
* 🐛 [Microsoft Store] Fix logic error in determining if the application was installed from the Microsoft Store by @guiyanakuang in [#1610](https://github.com/CrossPaste/crosspaste-desktop/pull/1610)
* 🐛 [Mac] Fix memory leak issue in MacosApi by @guiyanakuang in [#1619](https://github.com/CrossPaste/crosspaste-desktop/pull/1619)
* 🐛 [Linux] Fix clipboard detection and memory leak in pasteboard service by @guiyanakuang in [#1621](https://github.com/CrossPaste/crosspaste-desktop/pull/1621)

### New Features
* ⚡ Get current app's tray icon position using Mac API by @guiyanakuang in [#1612](https://github.com/CrossPaste/crosspaste-desktop/pull/1612)

### Improvements
* 👷 Calculate checksum for the built release files by @guiyanakuang in [#1602](https://github.com/CrossPaste/crosspaste-desktop/pull/1602)
* 👷 Improve Gradle task for Swift compilation by @guiyanakuang in [#1614](https://github.com/CrossPaste/crosspaste-desktop/pull/1614)

### Documents
* 📝 Add badge to readme by @guiyanakuang in [#1608](https://github.com/CrossPaste/crosspaste-desktop/pull/1608)
* 📝 Enhance conceptual documentation by @guiyanakuang in [#1616](https://github.com/CrossPaste/crosspaste-desktop/pull/1616)

**Full Changelog**: https://github.com/CrossPaste/crosspaste-desktop/compare/v1.0.2...v1.0.5

# [1.0.2] - 2024-08-01

### Bug Fixes
* 🐛 On Linux, We Should Retrieve the Distribution Version Instead of the Kernel Version by @guiyanakuang in [#1548](https://github.com/CrossPaste/crosspaste-desktop/pull/1548)
* 🐛 Fixed issue with duplicate unActive main window by @guiyanakuang in [#1575](https://github.com/CrossPaste/crosspaste-desktop/pull/1575)
* 🐛 Fix bug in adjusting the cleanupPercentage control spinner by @guiyanakuang in [#1589](https://github.com/CrossPaste/crosspaste-desktop/pull/1589)
* 🐛 Fix issue where right-clicking to activate the menu on Mac causes a light spot to appear in the top left corner of the desktop by @guiyanakuang in [#1593](https://github.com/CrossPaste/crosspaste-desktop/pull/1593)
* 🐛 Fix issue where modifying one configuration resets other modified configurations to their default values by @guiyanakuang in [#1595](https://github.com/CrossPaste/crosspaste-desktop/pull/1595)
* 🐛 Fix logic for hiding the tutorial button by @guiyanakuang in [#1598](https://github.com/CrossPaste/crosspaste-desktop/pull/1598)
* 🐛 Fix issue where menu does not display on Mac by @guiyanakuang in [#1600](https://github.com/CrossPaste/crosspaste-desktop/pull/1600)

### New Features
* 💄 First time user guide by @guiyanakuang in [#1596](https://github.com/CrossPaste/crosspaste-desktop/pull/1596)
* ✨ Support for identifying whether the installation source is from the microsoft store by @guiyanakuang in [#1554](https://github.com/CrossPaste/crosspaste-desktop/pull/1554)
* ✨ Choose between toast and system notification based on the main UI by @guiyanakuang in [#1560](https://github.com/CrossPaste/crosspaste-desktop/pull/1560)
* ✨ Implement linux notifications using JNA and libnotify by @guiyanakuang in [#1562](https://github.com/CrossPaste/crosspaste-desktop/pull/1562)
* ✨ Support search based on pasteboard source by @guiyanakuang in [#1585](https://github.com/CrossPaste/crosspaste-desktop/pull/1585)

### Improvements
* 💄 Providing i18n for search placeholder by @guiyanakuang in [#1552](https://github.com/CrossPaste/crosspaste-desktop/pull/1552)
* 🔨 Remove Notification References from commonMain by @guiyanakuang in [#1558](https://github.com/CrossPaste/crosspaste-desktop/pull/1558)
* 🔨 Modify the implementation to check the availability of the new version by @guiyanakuang in [#1564](https://github.com/CrossPaste/crosspaste-desktop/pull/1564)
* 💄 Prompt the user through the UI when a new version is available by @guiyanakuang in [#1566](https://github.com/CrossPaste/crosspaste-desktop/pull/1566)
* 💄 Unified system and desktop icons for Windows and Linux by @guiyanakuang in [#1568](https://github.com/CrossPaste/crosspaste-desktop/pull/1568)
* 💄 Applied theme color to default icons by @guiyanakuang in [#1571](https://github.com/CrossPaste/crosspaste-desktop/pull/1571)
* 🔨 Extracted redirect URLs into configuration by @guiyanakuang in [#1573](https://github.com/CrossPaste/crosspaste-desktop/pull/1573)
* 💄 Adjusted pasteboard menu and tooltip positions by @guiyanakuang in [#1577](https://github.com/CrossPaste/crosspaste-desktop/pull/1577)
* 💄 Added tooltip to show source and type when hovering over pasteboard icon by @guiyanakuang in [#1579](https://github.com/CrossPaste/crosspaste-desktop/pull/1579)
* ♻️ Rename Html type to Html Rich Text by @guiyanakuang in [#1587](https://github.com/CrossPaste/crosspaste-desktop/pull/1587)
* 💄 Optimize the UI of the spinner control by @guiyanakuang in [#1591](https://github.com/CrossPaste/crosspaste-desktop/pull/1591)


### Dependency Upgrades
* ⬆️ Bump org.signal:libsignal-client from 0.52.5 to 0.54.0 by @dependabot in [#1570](https://github.com/CrossPaste/crosspaste-desktop/pull/1570)

**Full Changelog**: https://github.com/CrossPaste/crosspaste-desktop/compare/v1.0.1...v1.0.2

# [1.0.1] - 2024-07-24

### Bug Fixes
* :bug: Fix the issue in build-release.yml where the Alibaba Cloud OSS version path was not set correctly by @guiyanakuang in https://github.com/CrossPaste/crosspaste-desktop/pull/1491
* :bug: Fix the issue where the transparent background did not adapt to the theme by @guiyanakuang in https://github.com/CrossPaste/crosspaste-desktop/pull/1493
* :bug: Fix the issue that FaviconLoader does not support concurrent loading by @guiyanakuang in https://github.com/CrossPaste/crosspaste-desktop/pull/1497
* :bug: Fix the issue of failing to retrieve the paste shortcut key by @guiyanakuang in https://github.com/CrossPaste/crosspaste-desktop/pull/1502
* :bug: Fix the bug of getting the wrong resolution of images by @guiyanakuang in https://github.com/CrossPaste/crosspaste-desktop/pull/1517
* :bug: Fix incorrect image annotation icon by @guiyanakuang in https://github.com/CrossPaste/crosspaste-desktop/pull/1519
* :bug: Avoid adding file type icons to the pasteboard by @guiyanakuang in https://github.com/CrossPaste/crosspaste-desktop/pull/1523
* :bug: Avoid writing to the clipboard on the main thread by @guiyanakuang in https://github.com/CrossPaste/crosspaste-desktop/pull/1525

### New Features
* :sparkles: Implement Thumbnail Loader by @guiyanakuang in https://github.com/CrossPaste/crosspaste-desktop/pull/1509
* :lipstick: get the icon for the current file type by @guiyanakuang in https://github.com/CrossPaste/crosspaste-desktop/pull/1500

### Improvements
* :heavy_plus_sign: Add imageio-core / imageio-jpeg for accelerated image loading by @guiyanakuang in https://github.com/CrossPaste/crosspaste-desktop/pull/1529
* :hammer: Refactoring ConcurrentLoader and adding UT by @guiyanakuang in https://github.com/CrossPaste/crosspaste-desktop/pull/1499
* :hammer: Refactor image loading implementation for better clarity, easier understanding, and improved extensibility by @guiyanakuang in https://github.com/CrossPaste/crosspaste-desktop/pull/1511
* :lipstick: Use CircularProgressIndicator as the image loading screen by @guiyanakuang in https://github.com/CrossPaste/crosspaste-desktop/pull/1513
* :lipstick: Modifying cursor state when copying pasteboards by @guiyanakuang in https://github.com/CrossPaste/crosspaste-desktop/pull/1527
* :lipstick: Avoid that the background of the pasteboard menu affects the recognition of the icons by @guiyanakuang in https://github.com/CrossPaste/crosspaste-desktop/pull/1506

### Dependency Upgrades
* ⬆️ Bump selenium from 4.22.0 to 4.23.0 by @dependabot in https://github.com/CrossPaste/crosspaste-desktop/pull/1514
* ⬆️ Bump io.mockk:mockk from 1.13.11 to 1.13.12 by @dependabot in https://github.com/CrossPaste/crosspaste-desktop/pull/1516
* ⬆️ Bump org.signal:libsignal-client from 0.52.2 to 0.52.5 by @dependabot in https://github.com/CrossPaste/crosspaste-desktop/pull/1515


**Full Changelog**: https://github.com/CrossPaste/crosspaste-desktop/compare/v1.0.0...v1.0.1

# [1.0.0] - 2024-07-12

### Release Notes
CrossPaste is a cross-platform pasteboard synchronization tool that supports macOS, Windows, and Linux. This tool provides seamless pasteboard sharing between different devices and operating systems, ensuring immediate data updates and consistency. Since the inception of the project, we have made over 700 commits, dedicated to creating a secure, efficient, and user-friendly pasteboard tool.

### Core Features
- **🖥️ Multi-OS Support**: Seamless operation across Mac, Windows, and Linux — for a truly universal pasteboard.
- **🔄 Real-Time Sync**: Automatically syncs your pasteboard in real time across devices, even between different OS like Mac and Windows.
- **🔒 End-to-End Encryption**: Utilizes Signal’s end-to-end encryption protocol to keep your data secure and private.
- **📋 Rich pasteboard Types**: Supports a wide variety of pasteboard content including text, URLs, rich text, HTML, images, and files.
- **🧹 Automatic Cleanup**: Features various automatic cleanup options to efficiently manage pasteboard space without manual effort.
- **🔌 Software Compatibility**: Supports pasteboard formats of mainstream software, such as Microsoft Office, Apple iWork, and LibreOffice.

### Future Outlook
We will continue to develop new features and optimize existing functionalities to meet user needs and expectations. Stay tuned for updates and improvements in subsequent versions.
