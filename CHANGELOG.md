# Changelog

All notable changes to this project will be documented in this file.

## [1.0.5] - 2024-08-05

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

## [1.0.2] - 2024-08-01

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

## [1.0.1] - 2024-07-24

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

## [1.0.0] - 2024-07-12

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
