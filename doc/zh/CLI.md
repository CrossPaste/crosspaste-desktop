---
outline: deep
---

# 命令行工具

CrossPaste 自带一个命令行客户端，与同一台机器上运行的桌面应用通信。你可以在终端和 shell 脚本里复制、粘贴、搜索和管理粘贴板历史。

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
| `paste [id]` | 显示最近一条粘贴，或按 ID 显示指定一条。 |
| `history` | 列出最近粘贴历史（`--limit`、`--type`、`--tag`、`--format`）。 |
| `search <query>` | 搜索粘贴历史（过滤参数与 `history` 相同）。 |
| `copy [text]` | 通过 CrossPaste 复制文本到剪贴板；接管道时读取 stdin。 |
| `delete <id>` | 按 ID 删除一条粘贴。 |
| `devices` | 列出已配对设备及连接状态。 |
| `config` | 查看配置；`config set <key> <value>` 修改配置。 |
| `tags` | 管理粘贴标签（`create`、`delete`）。 |
| `version` | 显示 CLI 版本。 |

全局选项：

- `--json` — 任意命令输出机器可读的 JSON。
- `--start` / `--no-start` — 应用未运行时，免询问直接拉起 / 永不拉起。默认行为：交互式终端会询问；非交互场景直接以退出码 3 快速失败（脚本绝不会卡在等待输入上）。

完整参考请运行 `crosspaste --help` 或 `crosspaste <command> --help`。

## 管道与脚本

CLI 为组合其他工具而设计。询问与进度信息都输出到 stderr，stdout 始终保持纯净、可安全接入管道。

从管道复制：

```sh
git log -1 --format=%H | crosspaste copy
cat notes.md | crosspaste copy
```

仅输出粘贴内容本体（无任何装饰），供后续管道使用。`--raw` 按存储原样输出——HTML/RTF 输出源码；`--summary` 则输出纯文本摘要：

```sh
crosspaste paste --raw | pbcopy
crosspaste paste --raw --no-newline > snippet.html
crosspaste paste --summary            # HTML/RTF 转为纯文本
```

列表与批量处理：

```sh
# 一行一个粘贴 ID——配合 xargs
crosspaste history --format id | xargs -n1 crosspaste delete

# JSON 输出——配合 jq
crosspaste search "TODO" --format json | jq '.items[].preview'
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
