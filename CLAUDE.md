# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

CrossPaste is a Kotlin Multiplatform application using Gradle. Key commands:

- **Run the application**: `./gradlew app:run`
- **Build**: `./gradlew build`
- **Run tests**: `./gradlew test`
- **Code formatting**: `./gradlew ktlintFormat`
- **Code style check**: `./gradlew ktlintCheck`
- **Run single test**: `./gradlew test --tests "ClassName.testMethodName"`

First startup downloads JBR (JetBrains Runtime) and gradle dependencies automatically.

## Code Architecture

### High-Level Structure
- **Desktop multiplatform app**: Built with Compose Multiplatform and Kotlin
- **Database**: SQLite with SQLDelight for type-safe queries
- **Networking**: Ktor for client/server communication with custom encryption
- **UI**: Compose Multiplatform with Material 3 design
- **DI**: Koin for dependency injection across modules

### Key Source Directories
- `app/src/commonMain/kotlin/`: Shared business logic across platforms
- `app/src/desktopMain/kotlin/`: Desktop-specific implementations
- `app/src/desktopTest/kotlin/`: Desktop platform tests
- `app/src/commonMain/sqldelight/`: Database schema definitions

### Core Module Organization
The application follows a modular architecture defined in `CrossPasteModule.kt`:

- **appModule()**: Core application services and managers
- **sqlDelightModule()**: Database layer and DAOs
- **networkModule()**: Client/server networking with encryption
- **securityModule()**: Cryptographic services and secure storage
- **pasteTypePluginModule()**: Handlers for different clipboard data types
- **pasteComponentModule()**: Clipboard monitoring and processing
- **uiModule()**: UI components and screen providers
- **viewModelModule()**: ViewModels for UI state management

### Key Architectural Patterns
- **Dependency Injection**: Koin modules organize services by domain
- **Repository Pattern**: DAOs abstract database operations
- **Plugin Architecture**: Extensible paste type handlers (text, images, files, etc.)
- **Platform Abstraction**: Common interfaces with platform-specific implementations
- **Secure Communication**: End-to-end encryption for cross-device sync

### Database Schema
SQLDelight manages database schema in `.sq` files:
- `PasteDatabase.sq`: Main paste item storage
- `PasteTaskDatabase.sq`: Background task management
- `SecureDatabase.sq`: Encrypted data storage
- `SyncRuntimeInfoDatabase.sq`: Device synchronization state

### Native Platform Integration
- **macOS**: Swift integration via `MacosApi.swift` compiled to dylib
- **Windows/Linux**: JNA for native API access
- **Global shortcuts**: Platform-specific keyboard listeners
- **System tray**: Cross-platform tray implementation

## Development Environment

### Platform-Specific Setup
- **macOS**: Xcode tools required for Swift compilation
- **All platforms**: JBR is automatically downloaded to `jbr/` directory
- **Proxy configuration**: Available in `gradle.properties` for restricted networks

### Testing
- Tests use MockK for mocking
- Platform-specific test utilities in `desktopTest/`
- System property `appEnv=TEST` is set automatically during test runs

## Code Style and Conventions

- **Commit messages**: Use emoji prefixes (see `doc/en/CommitMessage.md`)
- **Code formatting**: Enforced via ktlint with exclusions for generated code
- **Package structure**: Organized by feature domain under `com.crosspaste`
- **Platform files**: Use `.desktop.kt` suffix for platform-specific implementations
- **Utility usage**: Always check `com.crosspaste.utils` and prefer existing utility classes (e.g., FileUtils, DateUtils, StringUtils) instead of implementing new ones.

## Key Technical Details

- **Encryption**: Asymmetric encryption for secure device-to-device communication
- **Discovery**: mDNS/Bonjour for local network device discovery
- **Storage**: Local-only architecture with no cloud dependencies
- **Performance**: Coroutines for async operations, caching for clipboard history

## Commit Message Convention
Format: `<emoji code> <description>`

Types:
- :sparkles: new feature
- :bug: Bug fix
- :hammer: Refactoring
- :memo: docs
- :art: code style
- :zap: performance
- :white_check_mark: test
- :construction_worker: build/ci
- :heavy_plus_sign: add dependency
- :heavy_minus_sign: remove dependency
- :arrow_up: dependency upgrade
- :arrow_down: dependency downgrade

Examples from this project:
- :sparkles: add OCR support for images
- :bug: resolve navigation crash on back press
- :hammer: extract SwipeableDeviceRow component
- :construction_worker: bump kotlin to 2.0.0

Language: English for commit messages