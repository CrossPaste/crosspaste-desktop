# 常见问题

## 1. 查找不到附近的设备
CrossPaste 使用 DNS-SD（DNS Service Discovery）进行服务发现，就像局域网打印服务发现是类似的。如果你的局域网内服务发现没有正常工作，这可能有多个原因

1. 防火墙阻塞: 路由器或主机防火墙可能会阻止DNS-SD所需的UDP端口5353。这是mDNS(multicast DNS)使用的标准端口,用于服务发现。 防火墙也可能阻止组播流量,而DNS-SD依赖于组播通信。
2. 组播流量限制: 某些路由器可能默认禁用或限制组播流量,这会直接影响DNS-SD的功能。 一些路由器的IGMP snooping功能可能导致组播数据包无法正确转发。
3. 安全软件干扰: 主机上的安全软件(如杀毒软件或第三方防火墙)可能会错误地将DNS-SD流量识别为潜在威胁并阻止。

你可以通过下面的方式验证 DNS-SD 是否正常工作

1. 启动 CrossPaste 在各个设备上

2. 使用命令行工具查看是否能够发现服务

在 Mac 设备上，你可以执行下面的命令
```
dns-sd -B _crosspasteService._tcp
```

在 Windows 设备上，你需要首先安装 Bonjour SDK，https://download.developer.apple.com/Developer_Tools/bonjour_sdk_for_windows_v3.0/bonjoursdksetup.exe
```
dns-sd -B _crosspasteService._tcp
```

在 Linux 设备上，你需要确保安装了 ```avahi-utils``` 包，这里以 ubuntu 为例
```
sudo apt-get install avahi-utils
avahi-browse -r _crosspasteService._tcp
```

注意：已经添加到我的设备或者加入黑名单之后，这些设备不会再显示在附近的设备
如果命令行工具能够发现服务，但是 CrossPaste 无法发现，那么可能是 CrossPaste 的问题，你可以提交 issue 给我们

## 2. Linux：Wayland 应用中复制的内容没有被记录

在 Wayland 会话中，CrossPaste 通过 `ext-data-control-v1` / `wlr-data-control-unstable-v1` 协议直接读取合成器的剪贴板，因此原生 Wayland 应用中的复制（包括图片，例如 Spectacle 截图）都能被捕获。KDE Plasma、Sway、Hyprland、COSMIC、niri 以及其他基于 wlroots 的合成器都支持该协议。

GNOME（Mutter）没有实现这些协议。在 GNOME 上 CrossPaste 会退回到 XWayland 镜像的 X11 剪贴板，Mutter 会把所有类型的内容都同步到 X11，所以复制仍然可以被捕获。

如果在其他合成器上复制没有被捕获，请检查 `crosspaste.log` 中是否出现 `Compositor offers no data-control protocol`：这表示合成器缺少该协议，CrossPaste 使用了 X11 回退路径，只能看到合成器镜像到 XWayland 的内容。
