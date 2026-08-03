# [2.1.7] - 2026-08-03

## 🚀 The main window now opens on first launch
Fixed an issue on some platforms where the first launch only showed a tray icon and no window at all, making it look like the app "wouldn't open". The main window now opens automatically on first launch on every desktop platform.

## 🗄️ Huge clipboard entries no longer slow down your history
When you copy very large text, images, or similar content (over 8MB by default), CrossPaste no longer writes it into your history, preventing database bloat and UI slowdowns. Device sync skips these oversized entries too. Files are not affected.

## 🎨 New Appearance settings page
Settings now has an Appearance page where you can customize the height of the side search window, with a live preview as you drag the slider. Card title bars in the side window now also scale proportionally with card size for a more consistent look.

## 🍎 macOS: hide the Dock icon
A new setting lets you hide CrossPaste from the Dock and keep only the menu bar icon — for those who like a clean Dock.

## 🧩 Chrome extension improvements
Fixed an issue where the extension could not receive content pushed from the desktop app when encrypted sync was enabled. The extension also supports a newer, more secure pairing flow with the desktop app: both screens show the same numbers, and you just confirm they match.

## 🛡️ Hardened device sync
Device-to-device sync got a round of security and robustness hardening: sync connections and control requests now strictly verify the peer's identity, file transfers validate path legitimacy and cap resource usage, and malformed or maliciously crafted data can no longer disrupt normal syncing.

## 🔗 More reliable connections
Fixed several issues affecting connection stability: a single corrupted device record no longer prevents the whole device list from loading; failing to read device metadata no longer resets this device's identity (which could make paired devices suddenly appear as strangers); and device state changes now propagate reliably across the UI. The device list also correctly recognizes HarmonyOS devices now.

# [2.1.6] - 2026-07-14

## 🔁 Fixed a clipboard loop between the Chrome extension and desktop
Fixed an issue where running the Chrome extension and the desktop app together could bounce the same clipboard entry back and forth endlessly, flooding your history with duplicates. Both sides now recognize content written by the other and no longer re-trigger each other. Same-machine detection on Windows is fixed too — while the desktop app is running, the extension on the same computer now pauses its own clipboard capture instead of competing with it.

## 🪟 Upgraded clipboard monitoring on Windows
Clipboard monitoring on Windows now uses the system's recommended event notification mechanism, so copied content is picked up faster and more reliably. Source detection is fixed too — the "copied from" app shown in your history is now the program that actually wrote to the clipboard, not whichever window happened to be in the foreground.

## 🪟 Works on newer Windows builds without wmic
Newer Windows 11 builds have removed the wmic tool, which prevented CrossPaste from generating a device identifier. It now reads from the system registry instead, so CrossPaste works correctly on up-to-date Windows.

## 🔍 The search window no longer holds a grudge
Reopening the search window or changing your query now scrolls the list back to the top and selects the newest entry, instead of staying at your previous position or keeping a selection that no longer matches.

## 📶 Device discovery prefers your LAN
When your computer has multiple network connections (such as a cellular modem or a phone hotspot), CrossPaste now prefers the local network for discovering nearby devices, making discovery and connection more reliable.

## 🐧 New Linux ARM64 build
CrossPaste now ships a Linux ARM64 (aarch64) package, so ARM-based Linux devices are supported too.

# [2.1.5] - 2026-06-11

## 🛠️ Fixed the "Check for Updates" error
Fixed an error (Cannot call invokeAndWait from the event dispatcher thread) that appeared when clicking Check for Updates while a new version was available. This affected the Windows installer and macOS builds; manual update checks now work reliably again.

# [2.1.4] - 2026-06-10

## 🔄 Faster, more reliable reconnection
This release focuses on the connection between your devices. When you switch Wi-Fi networks, your network address changes, or your computer wakes from sleep, CrossPaste now rediscovers your other devices and reconnects much faster — no more being stuck "offline" for a long time. Even when a device's IP address changes, it reconnects reliably.

## 🆕 In-app changelog and upgrade reminder
You can now see what each update brings right inside the app. When a new version is available, a small dot appears in the left menu to remind you to upgrade.

## ⬆️ Windows (portable zip): update from within the app
The Windows portable (zip) build can now update itself. When a new version is available, CrossPaste downloads and verifies it, then swaps in the new build and restarts — no more manually downloading and extracting a fresh zip.

## 🖱️ Fixed a paste loop
Fixed an issue where pressing Cmd/Ctrl+V could trigger an endless paste loop in some situations. Pasting is now more reliable.

## ✨ Smoother device list
The device list no longer flickers while re-checking offline devices, so it stays steady.

## 🔤 No more garbled rich text
Fixed an issue where copying formatted text from apps such as JetBrains IDEs could produce garbled characters. Clipboard text encoding detection is now much more reliable.

## 🐧 Better clipboard on Linux
On Wayland desktops, copied content is now picked up faster. On Hyprland and Sway, CrossPaste also correctly identifies which app your copy came from.

# [2.1.3] - 2026-05-25

## 📤 Faster file sharing across devices
Sharing files between your devices is now snappier and more reliable. The device you copied from delivers files directly to your other devices, instead of making them ask for the data first.

## 🅾️ Redesigned OCR language settings
The text-from-image (OCR) language settings got a cleaner look, with each loaded language on its own card. You can now drag languages up and down to set their priority — handy when CrossPaste needs to pick which language to try first on multilingual images.

## 🛡️ Windows: heads-up when your network blocks CrossPaste
On Windows, if your network is set to "Public", the Windows Firewall silently blocks CrossPaste from finding nearby devices. CrossPaste now detects this and offers a one-click shortcut to the Windows setting where you can switch the network to Private.

## 🖱️ Smoother drag-and-drop
Dragging items from the CrossPaste side panel into other apps is much better now. On macOS, the drag preview no longer hides behind the paste window — you can finally see what you're dragging. Dragging stays smooth even with lots of items on screen, and you can now drag to reorder your search tags too.

## 🖼️ Animated GIFs and real video thumbnails in the side preview
GIFs in your clipboard history now actually animate (smoothly, even for large ones), and video files show a real thumbnail of the first frame instead of a generic icon.

# [2.0.0] - 2026-04-26

## 🌍 Chrome Extension — a first-class platform
CrossPaste now ships a Chrome extension that joins the sync network as a real platform. It supports image preview, large file download, right-click copy, and steps aside automatically when the desktop app is running on the same machine.

## 🔌 New WebSocket sync protocol
A bidirectional WebSocket sync protocol replaces the previous polling flow, with incremental pulling and an active liveness probe for snappier recovery after a disconnect.

## 🔐 Safer device pairing
A new Short-Authentication-String (SAS) pairing protocol prevents man-in-the-middle attacks while pairing devices, making it safer and easier to establish trust.

## 🇵🇹 Portuguese support
Added full Portuguese translations across the app.
