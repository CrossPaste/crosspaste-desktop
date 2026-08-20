---
outline: deep
---

# 命令行工具

CrossPaste 自带一个命令行客户端，与同一台机器上运行的 CrossPaste 应用通信——桌面应用，或无图形会话服务器上的 [headless 守护进程](#headless-守护进程linux-服务器)。你可以在终端和 shell 脚本里复制、粘贴、搜索和管理粘贴板历史。

CLI 是一个薄客户端：所有命令都经由本机的 CrossPaste 应用完成（通过 Unix domain socket），因此终端看到的历史、标签、设备与 UI 完全一致；从 CLI 复制的文本也会像普通粘贴一样同步到你的其他设备。

## 安装

CLI 二进制随所有桌面安装包一起分发，各平台的差异只在于如何进入 `PATH`：

| 平台 | 终端命令 | PATH 配置 |
|---|---|---|
| macOS | `crosspaste` | 应用首次启动时会提示安装命令行工具；也可以随时在应用内 **扩展 → 命令行** 页安装或修复。安装即创建 `/usr/local/bin/crosspaste` 软链接。 |
| Windows（安装包 / 微软商店） | `crosspaste-cli` | 自动。安装包注册 `crosspaste-cli` 执行别名（`crosspaste` 保留用于启动图形界面）。 |
| Linux（deb） | `crosspaste` | 自动。安装包创建 `/usr/bin/crosspaste` 软链接。 |
| Linux（tarball） | `crosspaste` | 手动：把 `<安装目录>/lib/app/bin/crosspaste-cli` 链接进 `PATH`，例如 `sudo ln -s <安装目录>/lib/app/bin/crosspaste-cli /usr/local/bin/crosspaste`。 |
| Windows（zip） | `crosspaste-cli` | 手动：把解压后的 `app\bin\` 目录加入 `PATH`。 |
| Linux（AppImage） | — | 二进制打包在镜像内 `bin/crosspaste-cli`，但挂载路径每次运行都会变化，无法建立稳定软链接。需要 CLI 请优先使用 deb 或 tarball。 |

下文示例使用 `crosspaste`；Windows 上请替换为 `crosspaste-cli`。

## 命令

| 命令 | 说明 |
|---|---|
| `status` | 显示应用是否在运行，以及版本、设备数、粘贴条数。永不自动拉起应用。 |
| `paste [id]` | 显示最近一条粘贴，或按 ID 显示指定一条。图片粘贴在支持的终端（iTerm2、WezTerm、Kitty、Ghostty）内联显示，并始终列出存储文件路径。 |
| `history [query]` | 列出最近粘贴历史；带查询词时执行搜索。过滤参数对等搜索窗口：`--type`（可重复指定多个）、`--tag`、`--sort newest\|oldest`，另有 `--limit`、`--format`。 |
| `pick [query]` | 全屏交互选择器：输入即模糊过滤，↑/↓ 选择条目，回车经 CrossPaste 复制选中条目（任意类型）。Ctrl-E 在 `$EDITOR` 中编辑；Ctrl-T/Ctrl-G 打开类型/标签选择条，←/→ 即时切换取值；Ctrl-S 切换排序；Tab 开合预览面板（图片条目在 iTerm2/WezTerm/Kitty/Ghostty 中内联显示第一张图，显示尺寸受限不占满屏）；`?` 显示帮助；Esc 取消（退出码 130）。 |
| `copy [text]` | 通过 CrossPaste 复制文本：写入历史并同步到其他设备；仅当桌面应用（而非 headless 守护进程）在运行时才会写系统剪贴板。接管道时读取 stdin。 |
| `edit [id]` | 在 `$VISUAL`/`$EDITOR` 中打开最近一条(或指定 ID 的)粘贴,编辑后的结果作为新条目复制。保存但未修改则不复制。HTML/RTF 编辑其源码,结果始终是文本粘贴。 |
| `delete <id>` | 按 ID 删除一条粘贴。 |
| `devices` | 列出已配对设备及连接状态。 |
| `pair` | 与附近设备配对：输入对方屏幕上显示的配对码（见[在终端里配对](#在终端里配对)）。 |
| `config` | 查看配置；`config set <key> <value>` 修改配置。 |
| `tags` | 管理粘贴标签（`create`、`delete`）。 |
| `version` | 显示 CLI 版本。 |

最常用的命令有单字母别名：`c` = `copy`、`p` = `paste`、`h` = `history`。

全局选项：

- `--json` — 任意命令输出机器可读的 JSON。
- `--start` / `--no-start` — 应用未运行时，免询问直接拉起 / 永不拉起。默认行为：交互式终端会询问；非交互场景直接以退出码 3 快速失败（脚本绝不会卡在等待输入上）。

完整参考请运行 `crosspaste --help` 或 `crosspaste <command> --help`。

## 管道与脚本

CLI 为组合其他工具而设计。询问与进度信息都输出到 stderr，stdout 始终保持纯净、可安全接入管道。

从管道复制——当输入来自管道且未指定命令时，CLI 直接按 `copy` 处理，命令名可以完全省略：

```sh
git log -1 --format=%H | crosspaste
cat notes.md | crosspaste
history | grep test | crosspaste copy   # 显式写法效果相同
```

仅输出粘贴内容本体（无任何装饰），供后续管道使用。`--raw` 按存储原样输出——HTML/RTF 输出源码；`--summary` 则输出纯文本摘要：

```sh
crosspaste paste --raw | pbcopy
crosspaste paste --raw --no-newline > snippet.html
crosspaste paste --summary            # HTML/RTF 转为纯文本
```

图片粘贴的 `--raw` 输出的是存储的图片字节，任何设备上复制的截图都能直接导出成文件或接给其他工具。仅支持单图片粘贴；一条记录含多张图片时会报错并列出各图片的存储路径：

```sh
crosspaste paste --raw > screenshot.png
crosspaste paste --raw | magick - -resize 50% small.png
```

Windows 上请经 cmd 重定向——`cmd /c "crosspaste-cli paste --raw > shot.png"`——或使用 PowerShell 7.4+。Windows PowerShell 5.1 的 `>` 会把原生命令输出按 UTF-16 文本重编码,损坏二进制数据。

列表与批量处理：

```sh
# 一行一个粘贴 ID——配合 xargs
crosspaste history --format id | xargs -n1 crosspaste delete

# 带过滤的搜索——查询词无需引号
crosspaste history TODO --type text --sort oldest

# JSON 输出——配合 jq
crosspaste history TODO --format json | jq '.items[].preview'
```

### 退出码

脚本可以依赖以下约定：

| 退出码 | 含义 |
|---|---|
| 0 | 成功 |
| 1 | 错误（请求失败、粘贴不存在等） |
| 2 | 参数错误（未知选项、缺少参数等） |
| 3 | CrossPaste 未运行（或仍在启动中） |

例如用 `crosspaste status`（永不自动拉起应用）做健康检查：

```sh
if ! crosspaste status > /dev/null; then
  echo "CrossPaste 尚未就绪"
fi
```

## 在终端里配对

`crosspaste pair` 让这台机器不经任何 UI 就能与另一台 CrossPaste 设备配对——这是所有同步功能的前提：

1. 命令列出局域网内发现的附近未配对设备（以及已知但尚未信任的设备）。按序号选择，或用 `--target <app-instance-id>` 跳过列表。
2. 目标设备（桌面或移动端，任何有屏幕的设备）会显示一个 6 位配对码；如有弹窗请在该设备上确认。
3. 在终端输入配对码（输入不回显）。成功后两台设备互相信任并开始同步。

配对本质是一次人工确认，因此 `pair` 要求交互式终端——向它管道输入会被视为参数错误。每个会话最多允许输入 5 次配对码；命令正常退出时会取消进行中的会话，被中断（Ctrl-C）遗留的会话则由服务端超时兜底回收。

如果设备列表为空，请确认对方设备上的 CrossPaste 正在运行、两台机器在同一网络，且防火墙没有拦截 multicast/mDNS 流量。

## headless 守护进程（Linux 服务器）

CrossPaste 也能在没有图形会话的机器上运行——例如通过 SSH 访问的 Linux 服务器。它就是同一个应用以 headless 模式（`--headless`）启动：完整的同步引擎、粘贴历史数据库和 CLI 端点，只是没有窗口、不接触系统剪贴板。在这样的机器上，`crosspaste copy` 直接把内容写入历史并同步到已配对设备；`crosspaste paste --raw` 取回你在其他设备上复制的内容。

headless 模式是自动检测的：机器上没有 `DISPLAY`/`WAYLAND_DISPLAY` 时，即使不带参数也会进入 headless 模式。

### 快速开始

运行守护进程有两条互斥的路径：让 CLI 按需拉起（本节），或交给 systemd 托管（[下一节](#用-systemd-用户服务常驻)）。二选一——如果你想要守护进程被托管、开机自启，请跳过 CLI 拉起，直接配置 systemd 单元。

按需拉起路径：按[安装](#安装)一节装好 deb（或解压 tarball），然后运行任意需要守护进程的命令（唯一例外是 `status`，它刻意永不自动拉起）：

```sh
crosspaste history
```

守护进程未运行时，CLI 会询问是否拉起（也可用 `--start` 免询问；脚本场景不会弹询问，直接返回退出码 3）。然后与其他设备配对：

```sh
crosspaste pair
```

图形应用与守护进程是同一个 peer：它们共享同一数据目录和单实例锁，同一用户同一时刻只会运行其中一个。

### 用 systemd 用户服务常驻

服务器上应该让守护进程被托管、开机自启。守护进程是按用户隔离的（数据与 socket 都在用户主目录下），所以要用 systemd **用户** 单元，而不是系统单元。

如果已经有一个 CLI 拉起的（非托管）守护进程在跑，**先停掉它再启用单元**——两者是同一个单实例守护进程，否则 systemd 每次启动都会撞单实例锁退出，`Restart=on-failure` 会不停重试：

```sh
kill "$(cat ~/.local/share/.crosspaste/crosspaste.pid)"   # 优雅停机（SIGTERM）
```

创建 `~/.config/systemd/user/crosspaste.service`：

```ini
[Unit]
Description=CrossPaste headless daemon

[Service]
# deb 安装路径；tarball 用 <安装目录>/bin/crosspaste
ExecStart=/usr/lib/crosspaste/bin/crosspaste --headless
Restart=on-failure

[Install]
WantedBy=default.target
```

启用它，并允许在未登录时运行：

```sh
systemctl --user daemon-reload
systemctl --user enable --now crosspaste
sudo loginctl enable-linger "$USER"   # 开机自启，注销后继续运行
```

CLI 刻意不提供 `daemon start/stop` 子命令：`systemctl --user stop crosspaste`（或直接 SIGTERM）即可优雅停机，`crosspaste status` 查看是否在运行。

一旦交给 systemd 托管，就保持托管：单元停止时如果某个 CLI 命令询问是否拉起 CrossPaste，请拒绝（或用 `--no-start`），改用 `systemctl --user start crosspaste`，让守护进程始终处于被托管状态。

### macOS（launchd）

headless 的 macOS 机器很少见，等价方式是 LaunchAgent。只在桌面应用从不运行的 Mac 上使用：应用与守护进程是共享单实例锁的同一个 peer，图形应用在跑时 launchd 会不断把守护进程重启到锁上。创建 `~/Library/LaunchAgents/com.crosspaste.daemon.plist`：

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
  <key>Label</key><string>com.crosspaste.daemon</string>
  <key>ProgramArguments</key>
  <array>
    <string>/Applications/CrossPaste.app/Contents/MacOS/CrossPaste</string>
    <string>--headless</string>
  </array>
  <key>RunAtLoad</key><true/>
  <key>KeepAlive</key><true/>
</dict>
</plist>
```

然后执行 `launchctl load ~/Library/LaunchAgents/com.crosspaste.daemon.plist`。

### 排障

- `crosspaste status` 查看守护进程是否在运行；退出码 3 表示未运行。
- 日志写入 `~/.local/share/.crosspaste/logs/`（macOS 为 `~/Library/Application Support/CrossPaste/logs/`）。
- 第二个实例会拒绝启动（stderr 报错 + 非零退出码）——包括桌面应用运行时再启动守护进程（图形应用与守护进程是同一用户下的同一个 peer）。
- 设备发现依赖局域网 mDNS/multicast；`crosspaste pair` 找不到设备时，请检查防火墙以及两台机器是否在同一网段。

## Shell 补全

CLI 可以为 bash、zsh、fish 生成补全脚本：

```sh
# bash（~/.bashrc）
source <(crosspaste --generate-completion bash)

# zsh（~/.zshrc）
source <(crosspaste --generate-completion zsh)

# fish
crosspaste --generate-completion fish > ~/.config/fish/completions/crosspaste.fish
```

## 颜色

人类可读输出使用少量颜色（粘贴类型、设备状态、应用状态）。输出不是终端时自动关闭颜色，并遵循 [`NO_COLOR`](https://no-color.org) 环境变量。

## 开发

无需打包发行版即可让 CLI 连上开发模式的应用实例：

```sh
# 终端 1：启动 dev 应用（其用户数据目录为 app/.user）
./gradlew app:run

# 终端 2：用 debug CLI 连接它
./gradlew :cli:run --args="history -n 5"
```

`:cli:run` 任务会链接宿主平台的 debug 可执行文件，并把 `CROSSPASTE_USER_DATA_DIR` 指向 `app/.user`，因此 CLI 会发现 dev 实例的 `cli-endpoint.json` 而不是已安装应用的。环境中显式设置的 `CROSSPASTE_USER_DATA_DIR` 优先于默认值；该变量同样适用于直接运行已构建的 CLI 二进制——适合在真实 TTY 下测试（颜色、完整终端宽度）：

```sh
CROSSPASTE_USER_DATA_DIR=app/.user cli/build/bin/macosArm64/debugExecutable/crosspaste-cli.kexe history
```

经 Gradle 转发的输出不是 TTY，CLI 探测不到窗口宽度，会回退到 79 列。可通过 `COLUMNS` 显式传入宽度（stdout 非交互时 CLI 都会遵循它）：

```sh
COLUMNS=$(tput cols) ./gradlew :cli:run --args="history"
```

设置了该覆盖变量后，CLI 不会再提示自动启动应用（那会启动已安装的正式版）；请自行启动目标实例。
