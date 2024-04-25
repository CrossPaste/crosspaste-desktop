import AppKit
import Cocoa
import Security

@_cdecl("getClipboardChangeCount")
public func getClipboardChangeCount(currentChangeCount: Int,
                                    isRemote: UnsafeMutablePointer<Bool>,
                                    isClipevery: UnsafeMutablePointer<Bool>) -> Int {
    let pasteboard = NSPasteboard.general
    let newChangeCount = pasteboard.changeCount

    isRemote.pointee = false
    isClipevery.pointee = false

    if newChangeCount != currentChangeCount, let items = pasteboard.pasteboardItems {
        for item in items {
            for type in item.types {
                if type.rawValue == "com.apple.is-remote-clipboard" {
                    isRemote.pointee = true
                } else if type.rawValue == "com.clipevery" {
                    isClipevery.pointee = true
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
        kSecValueData as String: passwordData
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

@_cdecl("bringToFront")
public func bringToFront(windowTitle: UnsafePointer<CChar>) -> UnsafePointer<CChar> {

    let currentApp = NSWorkspace.shared.frontmostApplication
    let currentAppName = currentApp?.bundleIdentifier ?? ""

    DispatchQueue.main.async {
        let title = String(cString: windowTitle)
        let windows = NSApplication.shared.windows
        for window in windows {
            if window.title == title {
                window.makeKeyAndOrderFront(nil)
                NSApp.activate(ignoringOtherApps: true)
                break
            }
        }
    }
    return UnsafePointer<CChar>(strdup(currentAppName))
}

@_cdecl("activeApp")
public func activeApp(appName: UnsafePointer<CChar>) {
    DispatchQueue.main.async {
        let appNameString = String(cString: appName)
        let apps = NSRunningApplication.runningApplications(withBundleIdentifier: appNameString)
        if let app = apps.first {
            app.activate(options: [.activateIgnoringOtherApps])
        }
    }
}