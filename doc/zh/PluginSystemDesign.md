# 插件系统设计讨论

> 状态：设计草案（2026-10-10）。本文记录对"让用户为不同剪贴板类型添加自定义右键功能"的可行性分析与设计结论，尚未实现。
> 英文版：[PluginSystemDesign.md](../en/PluginSystemDesign.md)

## 1. 结论概览

- **可行**。现有右键菜单在 `DesktopPasteMenuService` 中按 `PasteType` 硬编码为一张表，`ContextMenuGroup` 已支持子菜单，Roadmap 中已列出 "Plugin system"，插入点清晰。
- **桌面端第一阶段采用"脚本 / 外部命令插件"**，不采用 jar 插件。jar 插件能力最强但代价（API 稳定性、ClassLoader 隔离、jlink 裁剪、签名）数倍于脚本插件，放到第二阶段再评估。
- **插件描述（manifest）必须显式声明支持的系统与运行时**，应用市场、本地安装、右键菜单三处共用同一份兼容性判断逻辑，用户不会下载到无法运行的插件。
- **移动端不能运行进程插件**。移动端只支持"声明式插件"（无代码，纯数据）和系统级分享/快捷指令；如果将来需要用户代码，唯一政策安全且两端都有引擎的选择是 JavaScript。
- **manifest 格式、兼容性判断、菜单贡献接口、声明式执行器全部放在 `commonMain`**，进程执行器只放在 `desktopMain`，脚本引擎通过 `expect`/`actual` 按平台实现。

## 2. 插件形态比较

| 形态 | 优点 | 代价 | 结论 |
|---|---|---|---|
| 脚本 / 外部命令（manifest + 子进程） | 进程隔离，崩溃不影响主程序；任意语言；复用现有 CLI 作为数据接口；macOS 无签名问题 | 仅桌面可用；三个系统解释器环境差异需执行器兜底 | **桌面第一阶段** |
| 声明式（manifest 描述 HTTP / 模板替换 / URL scheme） | 无代码，三端通用；商店审核安全 | 表达能力有限 | **三端通用，移动端第一阶段** |
| 嵌入 JS 引擎（沙盒内运行用户代码） | 三端通用；能力介于两者之间 | 需维护多套引擎绑定与一致性测试 | 第二阶段，按需求再上 |
| jar 插件（URLClassLoader + ServiceLoader） | 能力最强，可贡献 Compose 子菜单、直接访问 DAO | 需独立发布 API 模块并保证二进制兼容；ClassLoader 隔离；Koin 边界；Conveyor jlink 模块补全；一个 jar 等于完全信任 | 暂不做 |
| 嵌入 Lua / Python | — | 体积与依赖大；iOS 审核灰区；Android 上 Python 需商业授权 | 不做 |

## 3. manifest 设计

```json
{
  "id": "com.example.translate",
  "version": "1.2.0",
  "minAppVersion": "2.3.0",
  "name": { "en": "Translate", "zh": "翻译" },
  "pasteTypes": ["text", "html"],
  "runtime": "process",
  "headless": false,
  "hosts": ["api.example.com"],
  "platforms": {
    "macos":   { "arch": ["arm64", "x64"], "command": ["/bin/sh", "run.sh"], "requires": ["curl"] },
    "linux":   { "command": ["/bin/sh", "run.sh"], "requires": ["curl"] },
    "windows": { "arch": ["x64"], "command": ["powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", "run.ps1"] }
  },
  "output": "replaceClipboard"
}
```

字段约定：

- `id`：反向域名，全局唯一；`version`：semver，用现有 `io.github.z4kn4fein:semver` 依赖比较。
- `minAppVersion`：市场索引和本地安装都按此过滤。
- `name`：locale 到 label 的映射，缺失时回退英文，接入 `GlobalCopywriter` 的语言切换。
- `pasteTypes`：对外固定的类型标识字符串（`text`、`html`、`rtf`、`url`、`color`、`image`、`files`），一旦公开不得改名。
- `runtime`：`process`（仅桌面）、`declarative`（三端）、将来的 `js`（三端）。市场按"当前平台 + 当前运行时支持"双重过滤。
- `headless`：能否在 Linux 无界面守护进程下运行。输出为通知或打开 URL 的插件在 headless 下无意义。
- `hosts`：允许访问的域名白名单。安装确认页展示，执行器拒绝向未声明域名发请求。
- `platforms`：**按平台分别声明命令**，而不是"一条命令 + 平台列表"。未出现的平台即不支持。键名小写 `macos` / `windows` / `linux` / `ios` / `android`，与 `Platform` 类中的线上值（`Macos` 等）做一次映射。
  - `arch` 可选，缺省为所有架构；带原生二进制的插件必须声明。`Platform.arch` 的原始值（`aarch64`、`amd64`、`x86_64`）需归一化为 `arm64` / `x64`。
  - `command` 为 argv 数组，不经 shell 解析；相对路径相对插件目录。
  - `requires`：外部依赖，安装或启用时在 PATH 探测，缺失即标记"不可用"并提示。
- `output`：结果处理方式，当前定义四条路径：写回剪贴板（`PasteboardService.tryWritePasteboard`）、新建一条 paste、弹通知（`NotificationManager`）、打开 URL（`UISupport.openUrlInBrowser`）。

为什么按平台分别声明命令：Windows 没有 bash，默认也没有 Python；macOS 12.3 起不再自带 python，仅随 Xcode 命令行工具提供；Windows 一定有 PowerShell 5.1 但不一定有 pwsh。现实的插件通常是 `run.sh` + `run.ps1` 两份实现。

## 4. 兼容性判断分三层

1. **市场索引**：由全部 manifest 生成，客户端按 `platforms`、`arch`、`minAppVersion`、`runtime` 过滤，不兼容的插件不列出或灰显。
2. **安装时**：本地再校验同样各项，加上 `requires` 探测，防止用户手动拷贝目录绕过市场。失败时展示具体原因（如"缺少 curl"）。
3. **运行时**：右键菜单只挂载启用且探测通过的插件；探测结果缓存，管理页可手动重新检测。

三处使用同一个放在 `commonMain` 的纯函数。

## 5. 桌面端进程执行器要统一处理的平台差异

这些无法由 manifest 表达，必须由执行器兜底，否则插件作者会在每个系统上各踩一遍：

1. **编码**：协议定义为 UTF-8 JSON 走 stdin/stdout。Windows PowerShell 5.1 默认使用 OEM 代码页，启动时需设置 `[Console]::OutputEncoding`；对 Python 注入 `PYTHONIOENCODING=utf-8`。读取时按 UTF-8 解码并归一化 CRLF。
2. **路径**：传给插件的文件路径保持系统原生格式，Windows 上不得转为正斜杠（见 `CLAUDE.md` 中 `NativeMessagingHostService` 的教训）。非 ASCII 用户目录必须测试。
3. **CLI 入口**：命令名在各系统不同（`crosspaste` / `crosspaste-cli`）且不一定在 PATH。执行器注入环境变量 `CROSSPASTE_CLI` 指向包内 CLI 绝对路径。更推荐把 paste 内容直接从 stdin 喂给插件，多数插件无需调用 CLI；数据格式与 CLI 的 `--json` 输出保持同一份。
4. **超时与进程树清理**：用 `ProcessHandle.descendants()` 连同子进程一起结束，Windows 与 POSIX 行为一致。
5. **各系统执行限制**：macOS 的 quarantine 属性与 TCC（见第 6 节）；Windows 需 `-ExecutionPolicy Bypass`，`.exe` 会触发 SmartScreen；Linux 需可执行位，AppImage 下路径每次变化。安装器导入时按系统处理并提示。
6. **工作目录与环境变量**：工作目录固定为插件目录；除显式注入的变量外继承用户环境，但清掉 JVM 自身设置的变量。
7. **并发与状态提示**：并发上限、stderr 落日志、执行中状态提示；`menuScope` 已在 IO 调度器上，不阻塞 UI。

## 6. macOS 专项

CrossPaste 走 Conveyor 打包、Developer ID 签名加公证、Sparkle 更新，不是 Mac App Store 分发，没有 App Sandbox。脚本插件没有系统层面的障碍，但有几条红线：

- **插件必须放在应用包外**，如 `~/Library/Application Support/CrossPaste/plugins`。写入 `.app` 内部会破坏签名，Gatekeeper 拒绝启动，Sparkle 增量更新失败。
- **Gatekeeper quarantine**：通过 `/bin/sh script.sh` 解释执行不受影响；直接执行下载来的二进制会被拦截。管理页需提示或在导入时清除该属性。
- **TCC 权限继承**：插件子进程以 CrossPaste 身份运行，访问桌面、文稿等受保护目录时系统以 CrossPaste 名义弹授权。
- **Hardened Runtime 与 jar**（仅 jar 阶段相关）：纯 class 加载没问题；jar 内含 dylib 或 JNA 原生库时会被 library validation 拦截，除非 entitlements 含 `disable-library-validation`。Conveyor 对 JVM 应用默认添加，需在发布产物中核实。
- **jlink 裁剪**（仅 jar 阶段相关）：Conveyor 默认按 jdeps 探测只打包用到的 JDK 模块，插件引用主程序未用的模块会 `ClassNotFoundException`，需在 `conveyor.conf` 显式补 `app.jvm.modules`。

Windows MSIX 与 Linux deb 无额外障碍；MSIX 下用户目录写入被虚拟化但可用。

## 7. 代码层面需要解决的阻碍

1. **菜单扩展点**：`DesktopPasteMenuService` 中 `mainPasteMenuItemsProvider` 与 `sidePasteMenuItemsProvider` 两份几乎相同的 `when` 表先合并，再引入 `PasteMenuContributor` 接口与注册表，把插件项合并进基础菜单。菜单项 lambda 在 UI 线程同步构造，插件列表必须预先缓存，不能在打开菜单时读磁盘。
2. **输入输出契约**：不暴露内部 `PasteItem`。读接口统一为 CLI 的 JSON，写接口为第 3 节的四条输出路径。文件与图片类型传路径列表。
3. **commonMain 与移动端兼容**：manifest 模型、`PasteMenuContributor`、兼容性判断、声明式执行器放 `commonMain`；子进程执行器只放 `desktopMain`。接口设计不得假设存在进程。
4. **管理界面**：Extensions 页（`ExtensionContentView`）已有 MCP、OCR、CLI 三项，新增 Plugins 子页：列表、启用/禁用、打开插件目录、手动重载、重新检测依赖。
5. **安全**：插件默认关闭，首次启用需明确确认（剪贴板常含密码与 token）；展示其声明的 `hosts` 与能力；第一版只支持本地目录安装，不做自动下载。
6. **持久化**：启用状态单独存储（建议一张 SQLDelight 表记录 id、版本、启用、安装来源、安装时间），不修改 manifest 文件。

仅 jar 插件才需要的额外工作：独立的 `crosspaste-plugin-api` 模块与版本协商、每个插件独立 ClassLoader 且父加载器只暴露 API 包、异常隔离、Compose 版本锁定、jlink 模块补全、macOS 原生库签名。

## 8. 移动端

### 8.1 为什么不能跑进程插件

- **iOS 没有子进程**，不能 fork/exec，没有 shell。App Store 审核指南 2.5.2 禁止下载并执行改变应用功能的代码。
- **Android 技术上能 `exec`，但没有可用环境**：只有 toybox 精简 shell，没有 Python、Node、curl。Android 10 起 targetSdk 29 以上禁止执行 App 可写目录中的文件（W^X）。Google Play 禁止从 Play 之外下载 dex、so，但豁免"在解释器中运行、只能间接访问 Android API 的代码"。
- **沙盒限制数据访问**：文件类型 paste 在移动端是容器内副本。
- **后台执行受限**：插件跑几秒即可能被挂起，长任务模型不成立。

### 8.2 移动端支持的扩展方式

1. **声明式插件**：manifest 描述 HTTP 请求模板、正则/模板替换、URL scheme 跳转。无代码，三端通用，审核安全。覆盖"翻译""缩短链接""发到 Webhook"等常见需求。
2. **系统级分享**：右键菜单加"分享到其他应用"（iOS Share Sheet、Android Intent）。iOS 另加"运行快捷指令"，通过 `shortcuts://run-shortcut?name=...&input=text` 交给用户自己的快捷指令；Android 对应 Share 到 Tasker 等自动化 App。零引擎成本。
3. **沙盒 JS 插件**（第二阶段，按需求再上）：见 8.4。Apple 2024 年更新的 4.7 条款允许在 WebKit / JavaScriptCore 中运行第三方 plug-in，前提是不暴露原生 API；上线前需对照当时条款原文复核。

### 8.3 移动端如何加载插件

声明式插件只是一个 JSON 文件，加载等于下载到沙盒容器、解析、校验、注册到菜单。

获取路径：

1. **应用内市场**：索引放在现有 `oss.crosspaste.com` 域名下，与桌面更新站共用 CDN。用 `commonMain` 的 Ktor client 拉索引并过滤，下载 manifest 到容器内 `plugins/<id>/`。下载、sha256 校验、重试复用 `ModuleLoader`（OCR 模型即此机制）。纯数据下载，不触犯商店政策。
2. **从配对的桌面端同步**：通过现有加密同步通道，把同时支持移动端的声明式插件一键推送到手机。差异化能力，建议第二阶段。
3. **URL scheme 与分享导入**：手机端注册与桌面相同的 `crosspaste://` scheme，支持 `crosspaste://plugin/install?url=...`；配文件关联（iOS "打开方式"、Android ACTION_VIEW）。
4. **内置插件**：若干官方声明式插件打进 App 资源，首次启动复制到插件目录，保证菜单不为空并作为样例。

运行时加载（全部在 `commonMain`）：

- `PluginRepository` 用 okio FileSystem 与 `UserDataPathProvider` 扫描插件目录，kotlinx.serialization 解析，跑兼容性纯函数，结果放入 StateFlow。
- 菜单贡献接口读该 StateFlow。移动端用户不能手改目录，安装/卸载后主动刷新即可；桌面端额外加目录监听或手动重载。
- 声明式执行器：HTTP 走 Ktor client，模板替换是一个小函数，打开 URL 走 `UISupport.openUrlInBrowser`，写回剪贴板走 `PasteboardService`。

需专门解决：

- **索引签名**：索引文件用 CrossPaste 私钥签名，客户端内置公钥校验，每个 manifest 在索引中带 sha256，防 CDN 被替换。
- **网络动作授权**：`hosts` 白名单 + 安装确认页展示"此插件会把内容发送到 api.example.com"，执行器拒绝未声明域名。手机上用户没有其他审计手段，尤其重要。

### 8.4 移动端运行时执行：两个系统各支持什么

| 能力 | iOS | Android |
|---|---|---|
| 起子进程 / shell | 完全不行 | 技术上能 `exec`，但只有 toybox 精简 shell |
| 运行下载来的二进制 | 不行 | targetSdk 29 以上 W^X 禁止，等于不行 |
| 系统自带 JS 引擎 | JavaScriptCore，系统框架，零体积，Kotlin/Native 可直接调 `platform.JavaScriptCore` | 仅 WebView 内 V8，需主线程创建 WebView，重且部分设备可禁用 |
| 可嵌入的 JS 引擎 | 不需要，JSC 够用 | QuickJS（原生库约 1MB，ES2023）或 Rhino（纯 Java 约 1.3MB，可与桌面 JVM 共用） |
| Lua / Python | 可嵌 Lua，但下载执行属审核灰区；Python 不现实 | LuaJ 可行；Python 需 Chaquopy，体积大且商业授权 |
| 商店政策 | 2.5.2 禁止下载执行改变功能的代码；4.7 对 WebKit / JSC 内的 plug-in 开口，不得暴露原生 API | 禁止下载 dex、so；豁免解释器内只能间接访问 API 的代码 |
| 第三方 JS 的 JIT | 不允许，JSC 对第三方 App 只走解释器 | QuickJS 无 JIT；Rhino 在 Android 只能解释模式 |

结论：移动端若要运行用户代码，唯一政策安全且两端都有引擎的选择是 **JavaScript**。Lua 与 Python 不值得投入。

JS 插件执行模型（三端一致）：

1. **每次调用新建干净的 JS context**，执行完销毁。毫秒级开销，换来插件间无状态泄漏、内存不增长。
2. **注入内容极窄**：`input` 对象（与 CLI JSON 同一格式）；`crosspaste` 宿主对象仅暴露 `fetch`（限 `hosts`）、`setClipboard`、`notify`、`openUrl`。无文件系统、无 DOM、无 require。这既是安全边界，也是 4.7 "不暴露原生 API" 的要求。
3. **入口固定**：`main.js` 导出 `run(input)` 返回 JSON，宿主决定写剪贴板还是通知；插件不直接触碰 UI。
4. **超时与内存由宿主强制**：JSC 用 `JSContextGroupSetExecutionTimeLimit`，QuickJS 用 interrupt handler 加 `JS_SetMemoryLimit`，Rhino 用 `observeInstructionCount`。移动端超时须远短于桌面。
5. `commonMain` 定义 `expect interface ScriptEngine`；iOS actual 包 JSC，Android actual 包 QuickJS 或 Rhino，桌面 actual 包 Rhino。调用方不感知引擎。

引擎选型取舍：Android 与桌面同用 Rhino 可把引擎绑定从三套减到两套（Rhino + JSC），代价是 Rhino 的 ES 支持停在 ES2015 加少量 ES2016+，落后于 QuickJS。无论选哪个都必须：

- **公开声明保守的 JS 子集**（建议 ES2015，不用 async/await 与 Promise）。
- **准备一致性测试**：同一组插件脚本在三个引擎上跑同一组断言并进 CI。三个引擎对正则、Unicode、Date 的处理细节都有差异，是 JS 阶段最大的隐性工作量。

## 9. 分阶段计划

| 阶段 | 范围 | 平台 |
|---|---|---|
| 1 | 抽出 `PasteMenuContributor`，现有菜单经其组装，不改任何用户可见行为 | 桌面 |
| 2 | manifest 模型 + 兼容性判断 + `process` 执行器 + Plugins 管理页 + 本地目录安装 | 桌面 |
| 3 | 声明式执行器 + "分享到其他应用" / "运行快捷指令" 菜单项 | 三端 |
| 4 | 应用市场：签名索引、sha256、下载安装、`crosspaste://plugin/install` | 三端 |
| 5 | 桌面到移动端的插件同步 | 三端 |
| 6 | 沙盒 JS 运行时（`ScriptEngine` expect/actual + 一致性测试） | 三端，按需求再启动 |
| 后续 | jar 插件 | 桌面，视 API 需求再评估 |

建议第一个 PR 只做阶段 1，让扩展点先稳定，后续工作可以在独立 PR 中并行推进。
