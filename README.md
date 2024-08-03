<div align="center">
   <img src="crosspaste_logo.webp" width=200 height=200>
   <h1>CrossPaste: Universal Pasteboard Across Devices</h1>
   <h4>Copy anything and paste it on any device, seamlessly.</h4>
</div>

**English** / [**简体中文**](./README.zh-CN.md)

[![Main CI Test](https://github.com/CrossPaste/crosspaste-desktop/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/CrossPaste/crosspaste-desktop/actions/workflows/ci.yml)
[![Build Release](https://github.com/CrossPaste/crosspaste-desktop/actions/workflows/build-release.yml/badge.svg)](https://github.com/CrossPaste/crosspaste-desktop/actions/workflows/build-release.yml)
![Dependabot](https://img.shields.io/badge/Dependabot-enabled-brightgreen.svg)
[![AGPL-3.0](https://img.shields.io/badge/License-AGPL%20v3-blue.svg)](https://github.com/CrossPaste/crosspaste-desktop/blob/main/LICENSE)

## ✨ Features

- **🔄 Real-time Sharing**: Instantly share pasteboard content across devices, seamlessly.
- **🖥️ Unified Cross-platform**: Consistent interface on Mac, Windows, and Linux. No need to change habits.
- **📋 Rich Type Support**: Easily handle various pasteboard types: Text、URL、HTML RTF、Images and Files.
- **🔒 End-to-End Encryption**: Using Signal protocol to fully protect your data security and privacy.
- **🔌 Wide Compatibility**: Support paste formats from major software like Office, iWork, and LibreOffice.
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

## 🤝 Support the project

- **🌟 Star this repository**: This is the easiest way to support CrossPaste and costs nothing.
- **🪲 Report bugs**: Report any bugs you find on the [issue tracker](https://github.com/CrossPaste/crosspaste-desktop/issues/new/choose).
- **📖 Translate**: Help translate and polish CrossPaste into your [language](https://github.com/CrossPaste/crosspaste-desktop/tree/main/composeApp/src/desktopMain/resources/i18n).
- **📝 Contribute**: [Contribute code](./Contributing.md), comment on issues, and any contributions that can help the project are welcome.






