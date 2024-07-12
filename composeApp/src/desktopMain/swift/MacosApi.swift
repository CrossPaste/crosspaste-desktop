import AppKit
import ApplicationServices
import Cocoa
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

    // Check the result
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

@_cdecl("getCurrentActiveApp")
public func getCurrentActiveApp() -> UnsafePointer<CChar>? {
    if let currentApp = NSWorkspace.shared.frontmostApplication {
        if let bundleIdentifier = currentApp.bundleIdentifier, let localizedName = currentApp.localizedName {
            return UnsafePointer<CChar>(strdup("\(bundleIdentifier)\n\(localizedName)"))
        }
    }
    return nil
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
    DispatchQueue.main.async {
        let windows = NSApplication.shared.windows
        for window in windows {
            if window.title == "CrossPaste" {
                if (NSApp.isActive) {
                    window.orderBack(nil)
                    NSApp.hide(nil)
                }
                break
            }
        }

        let appNameString = String(cString: appName)
        let apps = NSRunningApplication.runningApplications(withBundleIdentifier: appNameString)
        if let app = apps.first {
            app.activate(options: [.activateIgnoringOtherApps])
        }
    }
}

@_cdecl("searchToBack")
public func searchToBack(
    appName: UnsafePointer<CChar>,
    toPaste: Bool,
    keyCodesPointer: UnsafePointer<Int32>,
    count: Int
) {
    DispatchQueue.main.async {
        let windows = NSApplication.shared.windows
        for window in windows {
            if window.title == "CrossPaste Search" {
                if (NSApp.isActive) {
                    window.orderBack(nil)
                    NSApp.hide(nil)
                }
                break
            }
        }

        DispatchQueue.main.asyncAfter(deadline: .now() + 0.2) {
            if (toPaste) {
                simulatePasteCommand(keyCodesPointer: keyCodesPointer, count: count)
            }
        }
    }
}

@_cdecl("bringToFront")
public func bringToFront(windowTitle: UnsafePointer<CChar>) -> UnsafePointer<CChar> {

    let currentApp = NSWorkspace.shared.frontmostApplication
    let currentAppInfo = "\(currentApp?.bundleIdentifier ?? "")\n\(currentApp?.localizedName ?? "")"

    DispatchQueue.main.async {
        let title = String(cString: windowTitle)
        let windows = NSApplication.shared.windows
        for window in windows {
            if window.title == title {
                window.makeKeyAndOrderFront(nil)
                NSApp.setActivationPolicy(.accessory)
                NSApp.activate(ignoringOtherApps: true)
                break
            }
        }
    }
    return UnsafePointer<CChar>(strdup(currentAppInfo))
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

