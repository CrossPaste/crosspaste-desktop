# R2-00 Desktop Review Baseline

> 状态：完成
> 日期：2026-07-22
> 仓库：`crosspaste-desktop`
> 范围：版本、代码规模、模块/target、CI、测试资产、common 可复用门禁
> 非范围：mobile 平台实现、逐业务域逻辑正确性、数据库迁移细审、性能 profile

## 1. 冻结版本

| 项目 | 值 |
| --- | --- |
| 分支 | `main` |
| HEAD | `c34366d9c28f7251b2ab07978589ed5689e5a903` |
| Commit | `separate QR bearer token from SAS code at the type level (#4644)` |
| 工作区 | clean |

`crosspaste-mobile` 的 pinned desktop revision 在基线日指向同一个 SHA。该事实仅证明当前外部消费者的来源版本；mobile 仓库本身不属于本 session 的 review 范围。

## 2. 代码与模块基线

### Common 源码

| Source set | Kotlin/SQL 文件 | 约行数 | Desktop target |
| --- | ---: | ---: | --- |
| `core/src/commonMain` | 62 | 3,580 | JVM desktop、JS、host Native |
| `shared/src/commonMain` | 77 | 4,575 | JVM desktop、host Native |
| `app/src/commonMain` | 329 | 26,108 | 仅 JVM desktop |
| `shared-ui/src/commonMain` | 1 | 8 | 待 R2-12 归类 |
| 合计 | 469 | 34,271 | - |

真实 Gradle 依赖方向是 `app -> shared -> core`。`app/commonMain` 占 common 总量约 76%，但 app 模块在 `app/build.gradle.kts:71-76` 只声明 `jvm("desktop")`；目录名 `commonMain` 在这里不等于经过 Native/JS 验证的可移植模块。

### Desktop 实现与测试

| Source set | 文件 | 约行数 |
| --- | ---: | ---: |
| `app/src/desktopMain` | 315 | 36,429 |
| `app/src/desktopTest` | 150 | 25,214 |
| `shared/src/commonTest` | 1 | 25 |
| `core/src/commonTest` | 0 | 0 |

`app/src/desktopTest` 当前包含约 1,301 个 `@Test`，覆盖大量 common 业务逻辑，但这些测试只证明 JVM desktop 行为。

## 3. Target 与依赖事实

- `core` 声明 JVM desktop、JS browser 和当前 host 的 Kotlin/Native target，见 `core/build.gradle.kts:32-66`。
- `shared` 声明 JVM desktop 和当前 host 的 Kotlin/Native target，见 `shared/build.gradle.kts:48-75`。
- `app` 仅声明 JVM desktop，却在 `commonMain` 直接依赖 `kotlinx-coroutines-swing`、`material.desktop` 等 JVM/desktop 侧库，见 `app/build.gradle.kts:71-121`。
- 因此真正可作为跨平台模块发布和编译的边界目前只到 `core/shared`；`app/commonMain` 的跨项目复用是源码级复用，不是 Gradle/KMP 契约级复用。
- `shared` 通过 `api(project(":core"))` 导出 core，并通过 `api(sqldelight.coroutines.extensions)` 向消费者暴露 SQLDelight coroutine 类型。具体 API 泄漏在 R2-02 审查。

## 4. CI 与验证基线

### 仓库 CI

`.github/workflows/ci.yml:44-76` 的 app job 只执行：

```text
./gradlew app:build
```

该任务会编译 `core/shared` 的 desktop 依赖并运行 app desktop tests，但不会等价执行 `:core:build :shared:build`。

### 本轮执行

| 命令 | 结果 | 实际覆盖 |
| --- | --- | --- |
| `./gradlew app:build --no-daemon --stacktrace` | PASS，1m19s | app desktop compile/test/ktlint |
| `./gradlew :core:build :shared:build --no-daemon --stacktrace` | PASS，52s | core JS/host Native/desktop、shared host Native/desktop、shared native/common test、SQLDelight migration verification、ktlint |

第二条命令额外执行了 `core:compileKotlinNativeApp`、`core:compileKotlinJs`、`shared:compileKotlinNativeApp`、`shared:nativeAppTest` 和 `shared:verifyCommonMainDatabaseMigration`。当前基线全部通过。

## 5. Findings

### R2-00-001 [P1] CI 没有验证 common 模块的完整 target 与数据库迁移

**证据**

- `.github/workflows/ci.yml:75-76` 仅运行 `app:build`。
- app 只有 JVM desktop target，见 `app/build.gradle.kts:71-76`。
- core/shared 另外声明 JS 或 Native target，见 `core/build.gradle.kts:32-66`、`shared/build.gradle.kts:48-75`。
- 本轮只有显式执行 `:core:build :shared:build` 后，才出现 Native/JS 编译、`shared:nativeAppTest` 和 `shared:verifyCommonMainDatabaseMigration`。

**影响**

common 修改可以在 desktop app 编译和 1,301 个 JVM tests 全绿时，仍破坏 Native/JS 编译、Native test 或 SQLDelight migration verification。对已被其他产品消费的 common 代码，这是合并门禁缺失，而不是单纯覆盖率不足。

**修复建议**

CI app job 至少改为同时执行：

```text
./gradlew :core:build :shared:build :app:build
```

如果耗时需要拆分，应建立独立 `common-build` job，并让最终 `ci` job 强制依赖它。数据库变更不得绕过 `shared:verifyCommonMainDatabaseMigration`。

**验收**：在 core/shared Native-only 编译错误或非法 `.sqm` 迁移的测试分支上，PR CI 必须失败。

**状态**：Open，建议立即修复。

### R2-00-002 [P1] 最大的 common source set 没有跨平台编译契约

`app/commonMain` 有 329 个文件、约 26K 行，占 common 总量约 76%，但 app 模块只有 JVM target，并允许 `commonMain` 依赖 JVM/desktop 库。外部产品复用这些文件时，必须重新解释依赖和平台实现；desktop CI 无法证明它们在 Android/iOS/Kotlin Native 上可编译或语义一致。

这不是要求立即把整个 app 改成完整 KMP。R2-01/R2-02 应先识别真正 portable 的领域/协议代码，将其下沉到有 Native target 的 `core/shared`，并把 UI、Swing、desktop server/client 实现留在 app/desktop 层。新 common 代码不得继续仅凭目录名宣称可复用。

**验收**：形成明确的 portable package allowlist；allowlist 内代码由真实 KMP target 编译，外部消费者不再依赖全量源码同步。

**状态**：Open。

### R2-00-003 [P2] Portable tests 与 common 代码规模不匹配

34K common 源码中，`core/commonTest` 为空，`shared/commonTest` 只有 1 个测试文件；约 1,301 个测试全部位于 `app/desktopTest`。其中协议、序列化、同步状态、DAO、加密、文件索引等测试大量针对 common 类型，却只能在 JVM 上运行。

后续每个业务批次都应把纯逻辑测试下沉到对应模块的 `commonTest`；平台行为继续保留在 `desktopTest`。R2-13 汇总剩余缺口，而不是等到最后一次性搬迁。

**状态**：Open。

### R2-00-004 [P3] App ktlint 排除了测试和数据库路径

`app/build.gradle.kts:54-66` 排除全部 `desktopTest`，并按路径字符串排除任意 `/db/`。这意味着约 25K 行测试和手写数据库相关 Kotlin 不受 app ktlint 门禁约束，长期 review 中容易积累风格与可读性债务。

建议先区分 generated SQLDelight 文件与手写 DB 代码，只排除生成目录；逐步将 desktop tests 纳入 ktlint，并在单独提交中处理历史格式差异。

**状态**：Open，分别纳入 R2-07 和 R2-13。

## 6. 已确认结论

- 当前 desktop HEAD 在现有 app CI 命令和显式 core/shared 全目标构建下均为绿色。
- desktop 仓库的首要质量问题是“源码复用范围”大于“可验证 KMP 模块范围”。
- app desktop tests 数量充足，但 portable contract tests 极少，不能替代跨 target 验证。
- 本 session 后续只审 desktop；mobile 的平台适配、商业逻辑和 CI 由另一 session 维护。

## 7. 下一批交接

下一批：**R2-01 `core/src/commonMain` API、模型、序列化基础与依赖边界**。

开始时先读取本文件与 `index.md`，然后输出：

1. 62 个 core common 文件的 package/API 清单和依赖图。
2. public/internal API、可变状态、异常与默认值风险。
3. 所有序列化类型的 wire compatibility 表。
4. desktop/JS/Native provider 或 actual 差异。
5. 应下沉到 `core/commonTest` 的现有 desktop tests。
6. 稳定 finding ID、最小修复和验证命令。
