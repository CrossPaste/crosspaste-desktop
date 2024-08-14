<div align="center">
   <img src="marketing/en/marketing.webp" width="768px" height="432px" alt="poster" />
   <h1>CrossPaste: Universal Pasteboard Across Devices</h1>
   <h4>Copy anything and paste it on any device, seamlessly.</h4>
</div>

**English** / [**简体中文**](./README.zh-CN.md)

[![Main CI Test](https://github.com/CrossPaste/crosspaste-desktop/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/CrossPaste/crosspaste-desktop/actions/workflows/ci.yml)
[![Build Release](https://github.com/CrossPaste/crosspaste-desktop/actions/workflows/build-release.yml/badge.svg)](https://github.com/CrossPaste/crosspaste-desktop/actions/workflows/build-release.yml)
![Dependabot](https://img.shields.io/badge/Dependabot-enabled-2cbe4e.svg?logo=dependabot&logoColor=white)
[![Compose-Multiplatform](https://img.shields.io/badge/UI-Compose%20Multiplatform-3a7af2?logo=jetpackcompose&logoColor=white)](https://github.com/JetBrains/compose-multiplatform)
[![Realm](https://img.shields.io/badge/Database-Realm-39477F?logo=realm&logoColor=white)](https://github.com/realm/realm-kotlin)
![Kotlin](https://img.shields.io/badge/Lang-Kotlin-0095D5.svg?logo=kotlin&logoColor=white)
![OS](https://img.shields.io/badge/OS-Windows%20%7C%20macOS%20%7C%20Linux-2cbe4e)
[![Download](https://img.shields.io/badge/Download-v1.0.6-2cbe4e?logo=download&link=https://crosspaste.com/download)](https://crosspaste.com/download)
[![AGPL-3.0](https://img.shields.io/badge/License-AGPL%20v3-2cbe4e.svg)](https://github.com/CrossPaste/crosspaste-desktop/blob/main/LICENSE)

## ✨ Features

- **🔄 Real-time Sharing**: Instantly share pasteboard content across devices, seamlessly.
- **🖥️ Unified Cross-platform**: Consistent interface on Mac, Windows, and Linux. No need to change habits.
- **📋 Rich Type Support**: Easily handle various pasteboard types: Text、URL、HTML RTF、Images and Files.
- **🔒 End-to-End Encryption**: Using Signal protocol to fully protect your data security.
- **🌐 LAN-only Serverless**: Local storage, serverless architecture. Privacy protection, in your control.
- **🧹 Smart Space Management**: Auto-cleanup options manage pasteboard storage without manual effort.

## 🏗 Getting Started with Development

1. clone the repository

   ```bash
   git clone https://github.com/CrossPaste/crosspaste-desktop.git
   ```

2. Compile and run the application

   ```bash
   cd crosspaste-desktop
   ./gradlew composeApp:run
   ```
   
First start will download [JBR](https://github.com/JetBrains/JetBrainsRuntime) / [chromeDriver](https://googlechromelabs.github.io/chrome-for-testing/) / [chrome-headless-shell](https://googlechromelabs.github.io/chrome-for-testing/) / gradle dependencies.

If you encounter the following error:
```log
FAILURE: Build failed with an exception.

* What went wrong:
java.net.SocketException: Connection reset
> java.net.SocketException: Connection reset
```
you might need a VPN to download these dependencies.

To configure a proxy for gradle, add the following settings to [gradle.properties](./gradle.properties), and adjust the parameters to match your proxy configuration:
```properties
systemProp.https.proxyHost=localhost
systemProp.https.proxyPort=8080
systemProp.https.proxyUser=userid
systemProp.https.proxyPassword=password
systemProp.http.nonProxyHosts=*.nonproxyrepos.com|localhost
```

## 🗺️ Roadmap
CrossPaste is continuously evolving! We plan to add more useful features in future versions. Here's an overview of our near-term development plans:

- **v1.1.0**: Support for color pasteboard
- **v1.2.0**: Native pasteboard support, performance improvements
- **v1.3.0**: Introduction of command-line mode

This is just a small part of our plans. Want to learn more details and long-term plans? Check out our [full roadmap](./Roadmap.md).


## 🤝 Support the project

- **🌟 Star this repository**: This is the easiest way to support CrossPaste and costs nothing.
- **🪲 Report bugs**: Report any bugs you find on the [issue tracker](https://github.com/CrossPaste/crosspaste-desktop/issues/new/choose).
- **📖 Translate**: Help translate and polish CrossPaste into your [language](https://github.com/CrossPaste/crosspaste-desktop/tree/main/composeApp/src/desktopMain/resources/i18n).
- **📝 Contribute**: [Code](./Contributing.md), comment on issues, and any contributions that can help the project are welcome.
- **💖 Sponsor**: Support financially via [GitHub Sponsors](https://github.com/sponsors/CrossPaste) to help with ongoing development and maintenance.







