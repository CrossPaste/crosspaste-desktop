# Changelog

All notable changes to this project will be documented in this file.

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
