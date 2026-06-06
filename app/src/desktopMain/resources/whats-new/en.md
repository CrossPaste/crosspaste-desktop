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
