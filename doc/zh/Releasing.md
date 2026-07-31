# 发布流程

本文档说明 CrossPaste Desktop 的版本发布流程，以及为达成以下目标必须遵守的纪律：

- `main` 在维护一条 release line 的同时可以继续推进。
- 多个大特性可以以 flag 关闭状态合并进 `main`，而不阻塞小版本发布。
- 复用 `commonMain` 的移动端项目始终有一个稳定的、有名字的锚点可以同步。

只想看操作步骤的，直接跳到
[切小版本（X.Y.0）](#切小版本xy0)
或 [切补丁版本（X.Y.Z）](#切补丁版本xyz)。

---

## 分支模型

| 分支            | 用途                                                       |
| --------------- | ---------------------------------------------------------- |
| `main`          | 主干。始终可发。小特性和修复直接进来。大特性以独立分支进行，合并回 `main` 时必须 flag 关闭（"dark merge"）。 |
| `release/X.Y`   | 某一小版本的稳定线。决定发 `X.Y.0` 时从 `main` 切出。补丁版本（`X.Y.Z`）只能从这里切。 |
| `feature/*`     | 大特性的长存分支。只有当 flag 关闭也能干净编译和运行后，才能合并进 `main`。 |

### 为什么需要 release 分支？

直接在 `main` 上打 tag，意味着发版稳定性和主干迭代速度被耦合在一起：在 `vX.Y.0` 之上打 hotfix 必须把 `main` 后续合并的所有改动都带上。release 分支给我们一个干净的位置 cherry-pick 最少必要的修复，发出 patch 版本。

### 为什么大特性要 dark-merge？

长存分支会和 `main` 越走越远，rebase 越来越痛苦。把它早早合进来、但用 flag 关掉，可以让 `main` 保持持续集成，同时不让整个项目被这一个特性"是否完成"卡住。

flag 可以是编译期开关（如 `expect/actual` 默认关闭），也可以是运行期 `AppEnv` 配置。规则是：当 flag 关闭时，这个特性必须**完全不可见、零成本**——没有 UI、没有后台任务、没有 schema 迁移。

---

## tag 命名规则

tag 遵循 `MAJOR.MINOR.PATCH[-rc.N].REVISION`：

| tag                          | 所在分支          | channel  | `PRE_RELEASE` |
| ---------------------------- | ----------------- | -------- | ------------- |
| `2.1.0.2281`                 | `release/2.1`     | `stable` | `false`       |
| `2.1.1.2284`                 | `release/2.1`     | `stable` | `false`       |
| `2.1.0-rc.1.2280`            | `main`            | `main`   | `true`        |

- `REVISION` 是该 commit 处的 `git rev-list --count HEAD`。它只在**所在分支内**唯一，不是全局唯一。OSS channel 前缀避免不同分支恰好算出同一个 revision 时产物撞车。
- stable tag 必须打在 `release/X.Y` 分支上。
- RC tag 必须打在 `main` 上。
- CI workflow `.github/workflows/build-release.yml` 会通过分支包含性检查强制这两点。打在 feature 分支上的 tag 会拒绝构建。

---

## 版本属性文件

`app/src/desktopMain/resources/crosspaste-version.properties` 保存的是当前分支的有效版本号。CI 脚本 `.github/scripts/validateAndUpdateVersion.js` 会用它和 tag 对账，不一致直接 fail。

| 分支           | `version=` 应当是什么                                                  |
| -------------- | ---------------------------------------------------------------------- |
| `main`         | 下一次会从 `main` 切出去的 minor（如 `2.2.0`）。切完 release 分支后立即 bump。 |
| `release/X.Y`  | 这个分支上**即将打或最近打过**的 `X.Y.Z`。每次打 patch tag 前先 bump。 |

属性文件和 tag 不一致是硬错误——永远是改文件，不是绕开校验。

---

## OSS / 更新 channel

| channel  | OSS 前缀                                          | 谁会拿到                               |
| -------- | ------------------------------------------------ | -------------------------------------- |
| `stable` | `oss://crosspaste-desktop/stable/X.Y.Z.REV/`     | 默认更新通道的终端用户                 |
| `main`   | `oss://crosspaste-desktop/main/X.Y.Z-rc.N.REV/`  | 主动订阅了 pre-release 通道的用户      |

更新 manifest 必须分通道发布。stable 用户绝不能被自动升级到 RC 构建。

---

## 切小版本（X.Y.0）

在干净的 `main` 上运行：

```bash
./cut-release.sh 2.1.0
```

这个脚本做的事：

1. 确认你在 `main`、工作区干净、`main` 已与 `origin/main` 同步。
2. 确认 `crosspaste-version.properties` 是 `version=2.1.0`。
3. 从 `main` HEAD 创建 `release/2.1` 分支并 push。
4. 在 `main` 上创建 `chore/bump-to-2.2.0` 分支，把属性文件改成 `2.2.0` 并 push。你需要手动开 PR 合并它。

脚本输出 summary 之后：

```bash
git checkout release/2.1
REV=$(git rev-list --count HEAD)
git tag 2.1.0.$REV
git push origin 2.1.0.$REV
```

push 触发 `build-release.yml`。构建成功后，产物在 `oss://crosspaste-desktop/stable/2.1.0.$REV/`。

最后，在 `doc/MOBILE_COMPAT.md` 追加一行，记录这个 tag 起 `commonMain` 公开 API 的破坏性改动。

---

## 切补丁版本（X.Y.Z）

补丁在已经存在的 `release/X.Y` 分支上做。假设 `release/2.1` 已存在，要发 `2.1.1`：

```bash
git checkout release/2.1
git pull --ff-only

# 从 main cherry-pick 修复，一个一个来：
git cherry-pick <main-上的-sha>

# bump 属性文件（这一步故意手动——只有你能决定一组 cherry-pick 值不值得 bump patch）：
#   version=2.1.1
$EDITOR app/src/desktopMain/resources/crosspaste-version.properties
git add app/src/desktopMain/resources/crosspaste-version.properties
git commit -m ":construction_worker: bump release/2.1 to 2.1.1"
git push origin release/2.1

REV=$(git rev-list --count HEAD)
git tag 2.1.1.$REV
git push origin 2.1.1.$REV
```

如果修复只对 release 分支有意义（极少见，通常 `main` 也需要），直接在 `release/2.1` 上写，然后立刻 cherry-pick 回 `main`。"trunk-first" 规则防止分支漂移。

---

## 预发布 / RC 流程

用于在确定发 stable 之前先发一版包含大特性的构建给真用户试。

```bash
# 在 main 上，把大特性的 flag 打开：
git checkout main
git pull --ff-only

REV=$(git rev-list --count HEAD)
git tag 2.1.0-rc.1.$REV
git push origin 2.1.0-rc.1.$REV
```

CI 检测到 `-rc.` 段，自动设置 `PRE_RELEASE=true`，产物上传到 `oss://crosspaste-desktop/main/2.1.0-rc.1.$REV/`。更新 manifest 只对 `main` 通道发送。

通过往 `main` 推新 commit + 重新打 `rc.2`、`rc.3`… 来迭代。

最终要发 `2.1.0` stable 时，走 [小版本流程](#切小版本xy0)。RC tag 作为历史记录留在 `main` 上。

---

## 移动端同步

移动端项目把 `commonMain` 复制过去使用。契约是：

- 移动端**只**从打过 tag 的 commit 同步，永远不从 `main` HEAD 拉。
- 每次 stable 发版后，在 `doc/MOBILE_COMPAT.md` 追加一行：
  - tag（如 `2.1.0.2281`）。
  - 距离上一次 stable tag 的 `commonMain` 破坏性 API 改动。
  - 移动端是否已经消费。
- 改动 `commonMain` 公开 API 且属于破坏性的 commit，commit message 用 `:boom:` 前缀，方便整理兼容性表格时审计。

不确定某个 `commonMain` 改动是否对移动端造成破坏时，按"是"处理。误报零成本，漏报代价是一个移动端版本。

---

## 回滚

如果发出去的 stable 版本被发现有问题：

1. **不要删 tag。** 已经打出去的 artifact 是不可变历史。已经升级到坏版本的用户不会因为 tag 消失而自动回滚，反而隐藏了问题。
2. 在 `main` 修 bug，cherry-pick 到 `release/X.Y`，按 [补丁流程](#切补丁版本xyz) 发新版。
3. 如果严重到不能让用户停留在坏版本上，在更新 manifest 里把坏版本标记为 withdrawn，强制自动升级跳到补丁版本。

---

## checklist

打 stable tag 之前：

- [ ] 所有进行中的大特性，要么已完成，要么有 flag 在发布构建里关闭。
- [ ] release 分支上本地跑 `./gradlew app:desktopTest` 通过。
- [ ] `CHANGELOG.md` 更新好发布说明。
- [ ] release 分支上 `crosspaste-version.properties` 和 tag 版本一致。
- [ ] `MOBILE_COMPAT.md` 有这个 tag 对应的行（构建成功后填）。
