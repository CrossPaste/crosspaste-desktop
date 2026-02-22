import AppKit
import ApplicationServices
import Cocoa
import CoreGraphics
import ImageIO
import Security

@_cdecl("getPasteboardChangeCount")
public func getPasteboardChangeCount(currentChangeCount: Int,
                                     isRemote: UnsafeMutablePointer<Bool>,
                                     isCrossPaste: UnsafeMutablePointer<Bool>) -> Int {
    let pasteboard = NSPasteboard.general
    let newChangeCount = pasteboard.changeCount

    isRemote.pointee = false
    isCrossPaste.pointee = false

    if newChangeCount != currentChangeCount, let items = pasteboard.pasteboardItems {
        for item in items {
            for type in item.types {
                if type.rawValue == "com.apple.is-remote-clipboard" {
                    isRemote.pointee = true
                } else if type.rawValue == "com.crosspaste" {
                    isCrossPaste.pointee = true
                }
            }
        }
    }
    return newChangeCount
}

@_cdecl("getPassword")
public func getPassword(service: UnsafePointer<CChar>, account: UnsafePointer<CChar>) -> UnsafePointer<CChar>? {
    let serviceString = String(cString: service)
    let accountString = String(cString: account)

    let query: [String: Any] = [
        kSecClass as String: kSecClassGenericPassword,
        kSecAttrService as String: serviceString,
        kSecAttrAccount as String: accountString,
        kSecReturnData as String: kCFBooleanTrue!,
        kSecMatchLimit as String: kSecMatchLimitOne
    ]

    var item: CFTypeRef?
    let status = SecItemCopyMatching(query as CFDictionary, &item)

    guard status == errSecSuccess else { return nil }

    if let data = item as? Data, let password = String(data: data, encoding: .utf8) {
        return UnsafePointer<CChar>(strdup(password))
    }

    return nil
}

@_cdecl("setPassword")
public func setPassword(service: UnsafePointer<CChar>, account: UnsafePointer<CChar>, password: UnsafePointer<CChar>) -> Bool {
    let serviceString = String(cString: service)
    let accountString = String(cString: account)
    let passwordString = String(cString: password)

    let passwordData = passwordString.data(using: .utf8)!

    let query: [String: Any] = [
        kSecClass as String: kSecClassGenericPassword,
        kSecAttrService as String: serviceString,
        kSecAttrAccount as String: accountString,
        kSecValueData as String: passwordData,
        kSecAttrAccessible as String: kSecAttrAccessibleWhenUnlocked
    ]

    // Try to add the item to the keychain
    let status = SecItemAdd(query as CFDictionary, nil)

    if status == errSecDuplicateItem {
        // Entry already exists, update it instead
        let searchQuery: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: serviceString,
            kSecAttrAccount as String: accountString
        ]
        let updateFields: [String: Any] = [
            kSecValueData as String: passwordData
        ]
        let updateStatus = SecItemUpdate(searchQuery as CFDictionary, updateFields as CFDictionary)
        return updateStatus == errSecSuccess
    }

    return status == errSecSuccess
}

@_cdecl("updatePassword")
public func updatePassword(service: UnsafePointer<CChar>, account: UnsafePointer<CChar>, newPassword: UnsafePointer<CChar>) -> Bool {
    let serviceString = String(cString: service)
    let accountString = String(cString: account)
    let newPasswordString = String(cString: newPassword)

    let query: [String: Any] = [
        kSecClass as String: kSecClassGenericPassword,
        kSecAttrService as String: serviceString,
        kSecAttrAccount as String: accountString
    ]

    let updateFields: [String: Any] = [
        kSecValueData as String: newPasswordString.data(using: .utf8)!
    ]

    let status = SecItemUpdate(query as CFDictionary, updateFields as CFDictionary)

    return status == errSecSuccess
}

@_cdecl("deletePassword")
public func deletePassword(service: UnsafePointer<CChar>, account: UnsafePointer<CChar>) -> Bool {
    let serviceString = String(cString: service)
    let accountString = String(cString: account)

    let query: [String: Any] = [
        kSecClass as String: kSecClassGenericPassword,
        kSecAttrService as String: serviceString,
        kSecAttrAccount as String: accountString
    ]

    let status = SecItemDelete(query as CFDictionary)

    return status == errSecSuccess
}

@_cdecl("getComputerName")
public func getComputerName() -> UnsafePointer<CChar>? {
    if let computerName = Host.current().localizedName {
        return UnsafePointer<CChar>(strdup(computerName))
    }
    return nil
}

@_cdecl("getHardwareUUID")
public func getHardwareUUID() -> UnsafePointer<CChar>? {
    if let uuid = IOPlatformUUID() {
        return UnsafePointer<CChar>(strdup(uuid))
    }
    return nil
}

private func IOPlatformUUID() -> String? {
    let platformExpert = IOServiceGetMatchingService(kIOMasterPortDefault,
                                                     IOServiceMatching("IOPlatformExpertDevice"))
    defer { IOObjectRelease(platformExpert) }

    guard platformExpert != 0 else { return nil }
    guard let serialNumberAsCFString = IORegistryEntryCreateCFProperty(platformExpert,
                                                                       "IOPlatformUUID" as CFString,
                                                                       kCFAllocatorDefault,
                                                                       0).takeRetainedValue() as? String else { return nil }
    return serialNumberAsCFString
}

@_cdecl("saveAppIcon")
public func saveAppIcon(bundleIdentifier: UnsafePointer<CChar>, path: UnsafePointer<CChar>) {
    let bundleIdentifierString = String(cString: bundleIdentifier)
    let filePath = String(cString: path)

    if let app = NSRunningApplication.runningApplications(withBundleIdentifier: bundleIdentifierString).first {
        if let icon = app.icon, let tiffData = icon.tiffRepresentation {
            let bitmapImage = NSBitmapImageRep(data: tiffData)
            if let data = bitmapImage?.representation(using: .png, properties: [:]) {
                do {
                    try data.write(to: URL(fileURLWithPath: filePath))
                } catch {
                    print("Failed to write icon data to file: \(error)")
                }
            }
        }
    }
}

@_cdecl("mainToBack")
public func mainToBack(
    appName: UnsafePointer<CChar>
) {
    let appNameString = String(cString: appName)
    DispatchQueue.main.async {
        hideWindowAndActivateApp(hideTitle: "CrossPaste", appName: appNameString)
    }
}

@_cdecl("mainToBackAndPaste")
public func mainToBack(
    appName: UnsafePointer<CChar>,
    keyCodesPointer: UnsafePointer<Int32>,
    count: Int
) {
    let appNameString = String(cString: appName)
    let keyCodes = Array(UnsafeBufferPointer(start: keyCodesPointer, count: count))
    DispatchQueue.main.async {
        hideWindowAndActivateApp(hideTitle: "CrossPaste", appName: appNameString)

        DispatchQueue.main.asyncAfter(deadline: .now() + 0.2) {
            keyCodes.withUnsafeBufferPointer { buffer in
                guard let baseAddress = buffer.baseAddress else { return }
                simulatePasteCommand(keyCodesPointer: baseAddress, count: buffer.count)
            }
        }
    }
}

@_cdecl("searchToBack")
public func searchToBack(
    appName: UnsafePointer<CChar>
) {
    let appNameString = String(cString: appName)
    DispatchQueue.main.async {
        hideWindowAndActivateApp(hideTitle: "CrossPaste Search", appName: appNameString)
    }
}

@_cdecl("searchToBackAndPaste")
public func searchToBackAndPaste(
    appName: UnsafePointer<CChar>,
    keyCodesPointer: UnsafePointer<Int32>,
    count: Int
) {
    let appNameString = String(cString: appName)
    let keyCodes = Array(UnsafeBufferPointer(start: keyCodesPointer, count: count))
    DispatchQueue.main.async {
        hideWindowAndActivateApp(hideTitle: "CrossPaste Search", appName: appNameString)

        DispatchQueue.main.asyncAfter(deadline: .now() + 0.2) {
            keyCodes.withUnsafeBufferPointer { buffer in
                guard let baseAddress = buffer.baseAddress else { return }
                simulatePasteCommand(keyCodesPointer: baseAddress, count: buffer.count)
            }
        }
    }
}

private func hideWindowAndActivateApp(hideTitle: String, appName: String) {
    let windows = NSApplication.shared.windows
    for window in windows {
        if window.title == hideTitle {
            if NSApp.isActive {
                window.orderBack(nil)
                NSApp.hide(nil)
            }
            break
        }
    }

    let apps = NSRunningApplication.runningApplications(withBundleIdentifier: appName)
    if let app = apps.first {
        app.activate(options: [.activateIgnoringOtherApps])
    }
}

@_cdecl("setWindowLevelScreenSaver")
public func setWindowLevelScreenSaver(_ rawPtr: UnsafeRawPointer?) {
    guard let rawPtr = rawPtr else { return }

    let window = Unmanaged<NSWindow>.fromOpaque(rawPtr).takeUnretainedValue()

    DispatchQueue.main.async {
        if window.windowNumber <= 0 {
            return
        }

        let screenSaverLevel = CGWindowLevelForKey(.screenSaverWindow)
        window.level = NSWindow.Level(rawValue: Int(screenSaverLevel))
        window.collectionBehavior = [.canJoinAllSpaces, .fullScreenAuxiliary]
    }
}

#if arch(arm64)
@available(macOS 26.0, *)
private func setupLiquidGlass(containerView: NSView, contentView: NSView, identifier: NSUserInterfaceItemIdentifier) {
    let glassContainer = NSGlassEffectContainerView(frame: containerView.bounds)
    glassContainer.autoresizingMask = [.width, .height]
    glassContainer.identifier = identifier

    let glassView = NSGlassEffectView(frame: glassContainer.bounds)
    glassView.autoresizingMask = [.width, .height]

    glassContainer.addSubview(glassView)
    containerView.addSubview(glassContainer, positioned: .below, relativeTo: contentView)
}
#endif

private func setupFallbackBlur(containerView: NSView, contentView: NSView, identifier: NSUserInterfaceItemIdentifier) {
    let blurView = NSVisualEffectView(frame: containerView.bounds)
    blurView.autoresizingMask = [.width, .height]
    blurView.blendingMode = .behindWindow

    if let specificMaterial = NSVisualEffectView.Material(rawValue: 26) {
        blurView.material = specificMaterial
    } else {
        blurView.material = .hudWindow
    }
    blurView.identifier = identifier
    containerView.addSubview(blurView, positioned: .below, relativeTo: contentView)
}

@_cdecl("applyAcrylicBackground")
public func applyAcrylicBackground(_ rawPtr: UnsafeRawPointer?, _ isDark: Bool) {
    guard let rawPtr = rawPtr else { return }
    let window = Unmanaged<NSWindow>.fromOpaque(rawPtr).takeUnretainedValue()

    DispatchQueue.main.async {
        if window.windowNumber <= 0 {
            return
        }

        window.appearance = nil
        window.appearance = NSAppearance(named: isDark ? .vibrantDark : .vibrantLight)

        window.isOpaque = false
        window.backgroundColor = .clear
        window.titlebarAppearsTransparent = true
        window.styleMask.insert(.fullSizeContentView)

        guard let contentView = window.contentView,
              let containerView = contentView.superview
        else {
            return
        }

        contentView.wantsLayer = true
        contentView.layer?.backgroundColor = NSColor.clear.cgColor

        let kLayerId = NSUserInterfaceItemIdentifier("CrossPasteBackgroundLayer")
        containerView.subviews.filter { $0.identifier == kLayerId }.forEach { $0.removeFromSuperview() }

        #if arch(arm64)
        if #available(macOS 26.0, *) {
            setupLiquidGlass(containerView: containerView, contentView: contentView, identifier: kLayerId)
        } else {
            setupFallbackBlur(containerView: containerView, contentView: contentView, identifier: kLayerId)
        }
        #else
        setupFallbackBlur(containerView: containerView, contentView: contentView, identifier: kLayerId)
        #endif
    }
}

@_cdecl("getCurrentActiveAppInfo")
public func getCurrentActiveAppInfo() -> UnsafePointer<CChar>? {
    if let currentApp = NSWorkspace.shared.frontmostApplication {
        if let bundleIdentifier = currentApp.bundleIdentifier, let localizedName = currentApp.localizedName {
            return UnsafePointer<CChar>(strdup("\(bundleIdentifier)\n\(localizedName)"))
        }
    }
    return nil
}

@_cdecl("getRunningApplications")
public func getRunningApplications() -> UnsafePointer<CChar>? {
    let apps = NSWorkspace.shared.runningApplications
    var result = [String]()
    for app in apps {
        guard app.activationPolicy == .regular,
              let bundleId = app.bundleIdentifier,
              let name = app.localizedName else { continue }
        result.append("\(bundleId)\n\(name)")
    }
    if result.isEmpty { return nil }
    return UnsafePointer<CChar>(strdup(result.joined(separator: "\n\n")))
}

@_cdecl("bringToFront")
public func bringToFront(windowTitle: UnsafePointer<CChar>) {
    let title = String(cString: windowTitle)
    DispatchQueue.main.async {
        let app = NSApplication.shared
        app.activate(ignoringOtherApps: true)
        let windows = app.windows
        for window in windows {
            if window.title == title {
                window.makeKeyAndOrderFront(nil)
                if title == "CrossPaste" {
                    if app.activationPolicy() != .regular {
                        app.setActivationPolicy(.regular)
                        app.activate(ignoringOtherApps: true)
                    }
                } else if title == "CrossPaste Search" {
                    if app.activationPolicy() != .accessory {
                        app.setActivationPolicy(.accessory)
                        app.activate(ignoringOtherApps: true)
                    }
                }
                window.orderFrontRegardless()
                window.makeKey()
                break
            }
        }
    }
}

@_cdecl("simulatePasteCommand")
public func simulatePasteCommand(keyCodesPointer: UnsafePointer<Int32>, count: Int) {
    if (count <= 0) {
        return
    }

    let source = CGEventSource(stateID: .combinedSessionState)
    let keyCodes = UnsafeBufferPointer(start: keyCodesPointer, count: count)

    var flags = CGEventFlags()

    // Identify and set modifier flags
    for keyCode in keyCodes {
        switch keyCode {
        case 55:  // Command key
            flags.insert(.maskCommand)
        case 56:  // Shift key
            flags.insert(.maskShift)
        case 58:  // Option key
            flags.insert(.maskAlternate)
        case 59:  // Control key
            flags.insert(.maskControl)
        default:
            break
        }
    }

    for keyCode in keyCodes {
        if let keyDown = CGEvent(keyboardEventSource: source, virtualKey: CGKeyCode(UInt16(keyCode)), keyDown: true) {
            keyDown.flags = flags
            keyDown.post(tap: .cghidEventTap)
        }
    }

    for keyCode in keyCodes.reversed() {
        if let keyUp = CGEvent(keyboardEventSource: source, virtualKey: CGKeyCode(UInt16(keyCode)), keyDown: false) {
            keyUp.flags = []
            keyUp.post(tap: .cghidEventTap)
        }
    }
}

@_cdecl("checkAccessibilityPermissions")
public func checkAccessibilityPermissions() -> Bool {
    let checkOptionPrompt = kAXTrustedCheckOptionPrompt.takeUnretainedValue() as String
    let options = [checkOptionPrompt: false] as CFDictionary
    let accessEnabled = AXIsProcessTrustedWithOptions(options)

    return accessEnabled
}

@_cdecl("saveIconByExt")
public func saveIconByExt(ext: UnsafePointer<CChar>, path: UnsafePointer<CChar>) {
    let extString = String(cString: ext)
    let filePath = String(cString: path)

    let icon = NSWorkspace.shared.icon(forFileType: extString)
    if let tiffData = icon.tiffRepresentation {
        let bitmapImage = NSBitmapImageRep(data: tiffData)
        if let data = bitmapImage?.representation(using: .png, properties: [:]) {
            do {
                try data.write(to: URL(fileURLWithPath: filePath))
            } catch {
                print("Failed to write icon data to file: \(error)")
            }
        }
    }
}

@_cdecl("createThumbnail")
public func createThumbnail(
    originalImagePath: UnsafePointer<CChar>,
    thumbnailImagePath: UnsafePointer<CChar>,
    metadataPath: UnsafePointer<CChar>
) -> Bool {
    let originalImagePathString = String(cString: originalImagePath)
    let thumbnailImagePathString = String(cString: thumbnailImagePath)
    let metadataPathString = String(cString: metadataPath)

    guard let imageSource = CGImageSourceCreateWithURL(URL(fileURLWithPath: originalImagePathString) as CFURL, nil),
          let originalImage = CGImageSourceCreateImageAtIndex(imageSource, 0, nil) else {
        return false
    }

    let originalWidth = originalImage.width
    let originalHeight = originalImage.height

    // Check if the file size can be obtained, return false if it can't
    guard let fileSize = try? FileManager.default.attributesOfItem(atPath: originalImagePathString)[.size] as? Int64 else {
        return false
    }

    let thumbnailWidth: Int
    let thumbnailHeight: Int

    if originalWidth <= originalHeight {
        thumbnailWidth = 200
        thumbnailHeight = 200 * originalHeight / originalWidth
    } else {
        thumbnailWidth = 200 * originalWidth / originalHeight
        thumbnailHeight = 200
    }

    let thumbnailRect = CGRect(x: 0, y: 0, width: thumbnailWidth, height: thumbnailHeight)

    guard let colorSpace = originalImage.colorSpace,
          let context = CGContext(data: nil,
                                  width: thumbnailWidth,
                                  height: thumbnailHeight,
                                  bitsPerComponent: 8,
                                  bytesPerRow: 0,
                                  space: colorSpace,
                                  bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue) else {
        return false
    }

    context.interpolationQuality = .high
    context.draw(originalImage, in: thumbnailRect)

    guard let thumbnailImage = context.makeImage() else {
        return false
    }

    let thumbnailUrl = URL(fileURLWithPath: thumbnailImagePathString)
    guard let destination = CGImageDestinationCreateWithURL(thumbnailUrl as CFURL, kUTTypePNG, 1, nil) else {
        return false
    }

    CGImageDestinationAddImage(destination, thumbnailImage, nil)

    guard CGImageDestinationFinalize(destination) else {
        return false
    }

    // Save metadata
    let metadata = """
    size=\(fileSize)
    dimensions=\(originalWidth) x \(originalHeight)
    """

    do {
        try metadata.write(toFile: metadataPathString, atomically: true, encoding: .utf8)
    } catch {
        return false
    }

    return true
}

var statusItem: NSStatusItem?
var menu: NSMenu?
var callbackPointer: (@convention(c) (Int) -> Void)?

class MenuActionHandler: NSObject {
    let itemId: Int

    init(itemId: Int) {
        self.itemId = itemId
    }

    @objc func menuItemClicked(_ sender: NSMenuItem) {
        callbackPointer?(itemId)
    }
}

var handlers: [MenuActionHandler] = []

var leftClickCallbackPointer: (@convention(c) () -> Void)?

class StatusItemHandler: NSObject {
    @objc func statusItemClicked(_ sender: Any?) {
        let event = NSApp.currentEvent

        if event?.type == .rightMouseUp || event?.modifierFlags.contains(.control) == true {
            if let button = statusItem?.button, let menu = menu {
                statusItem?.menu = menu
                button.performClick(nil)
                statusItem?.menu = nil
            }
        } else {
            leftClickCallbackPointer?()
        }
    }
}


var statusItemHandler: StatusItemHandler?

@_cdecl("trayInit")
public func trayInit(iconData: UnsafePointer<UInt8>,
                     iconDataLength: Int,
                     tooltip: UnsafePointer<CChar>,
                     leftClickCallback: @escaping @convention(c) () -> Void) -> Bool {
    return autoreleasepool {
        if !Thread.isMainThread {
            var result = false
            DispatchQueue.main.sync {
                result = trayInit(iconData: iconData,
                                  iconDataLength: iconDataLength,
                                  tooltip: tooltip,
                                  leftClickCallback: leftClickCallback)
            }
            return result
        }

        leftClickCallbackPointer = leftClickCallback

        statusItem = NSStatusBar.system.statusItem(withLength: NSStatusItem.variableLength)

        let data = Data(bytes: iconData, count: iconDataLength)
        if let image = NSImage(data: data) {
            image.size = NSSize(width: 20, height: 20)
            image.isTemplate = true
            statusItem?.button?.image = image
        } else {
            statusItem?.button?.title = "App"
        }

        let tooltipStr = String(cString: tooltip)
        statusItem?.button?.toolTip = tooltipStr

        menu = NSMenu()

        if let button = statusItem?.button {
            statusItemHandler = StatusItemHandler()
            button.action = #selector(StatusItemHandler.statusItemClicked(_:))
            button.target = statusItemHandler
            button.sendAction(on: [.leftMouseUp, .rightMouseUp])
        }

        return true
    }
}

@_cdecl("trayAddMenuItem")
public func trayAddMenuItem(itemId: Int,
                            title: UnsafePointer<CChar>,
                            enabled: Bool) {
    autoreleasepool {
        if !Thread.isMainThread {
            DispatchQueue.main.sync {
                trayAddMenuItem(itemId: itemId, title: title, enabled: enabled)
            }
            return
        }

        let titleStr = String(cString: title)
        let menuItem = NSMenuItem(title: titleStr, action: nil, keyEquivalent: "")
        menuItem.isEnabled = enabled

        let handler = MenuActionHandler(itemId: itemId)
        handlers.append(handler)
        menuItem.target = handler
        menuItem.action = #selector(MenuActionHandler.menuItemClicked(_:))

        menu?.addItem(menuItem)
    }
}

@_cdecl("trayAddSeparator")
public func trayAddSeparator() {
    autoreleasepool {
        if !Thread.isMainThread {
            DispatchQueue.main.sync {
                trayAddSeparator()
            }
            return
        }

        menu?.addItem(NSMenuItem.separator())
    }
}

@_cdecl("traySetCallback")
public func traySetCallback(callback: @escaping @convention(c) (Int) -> Void) {
    callbackPointer = callback
}

@_cdecl("trayUpdateMenuItem")
public func trayUpdateMenuItem(itemId: Int,
                               title: UnsafePointer<CChar>?,
                               enabled: Bool) {
    autoreleasepool {
        if !Thread.isMainThread {
            DispatchQueue.main.sync {
                trayUpdateMenuItem(itemId: itemId, title: title, enabled: enabled)
            }
            return
        }

        guard let menuItems = menu?.items else { return }

        for (_, handler) in handlers.enumerated() {
            if handler.itemId == itemId {
                let menuItem = menuItems[itemId]

                if let title = title {
                    menuItem.title = String(cString: title)
                }
                menuItem.isEnabled = enabled
                break
            }
        }
    }
}

@_cdecl("trayCleanup")
public func trayCleanup() {
    autoreleasepool {
        if !Thread.isMainThread {
            DispatchQueue.main.sync {
                trayCleanup()
            }
            return
        }

        if let statusItem = statusItem {
            NSStatusBar.system.removeStatusItem(statusItem)
        }
        statusItem = nil
        menu = nil
        handlers.removeAll()
        callbackPointer = nil
    }
}
