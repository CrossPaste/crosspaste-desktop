# R2-00 Desktop Review Remediation

> 状态：完成
> 日期：2026-07-22
> 基线：`c34366d9c28f7251b2ab07978589ed5689e5a903`
> 范围：R2-00 findings 的第一轮修复，不包含 R2-01 业务代码细审

## 1. 修复结果

### R2-00-001 [P1] Resolved

CI 的 desktop build 命令改为：

```text
./gradlew :core:build :shared:build :app:build
```

这会显式执行 core 的 desktop、JS、host Native 编译与测试，shared 的 desktop、host Native 编译与测试，以及 SQLDelight migration verification。`app:build` 不再被当作 portable common 的替代门禁。

### R2-00-002 [P1] In progress

当前可移植契约边界固定为 `core` 和 `shared` 两个真实 KMP 模块：

- 新增的跨 desktop/mobile 领域模型、协议与纯逻辑必须进入 `core/commonMain` 或 `shared/commonMain`。
- portable 代码必须有对应 `commonTest`，并由真实 JVM/JS/Native target 验证；平台测试不能替代该契约。
- `app/commonMain` 仍视为 desktop application source。其目录名不构成可移植承诺，现有源码在 R2-01/R2-02/R2-11 按依赖逐批下沉。
- UI、Swing、desktop 网络/存储实现继续留在 app 层，不为追求目录统一强行移入 KMP 模块。

本轮通过 CI 对 `core/shared` 的独立完整构建落实了边界，但 `app/commonMain` 的 26K 历史源码尚未完成分类和迁移，因此 finding 保持 In progress。

### R2-00-003 [P2] In progress

从 `app/desktopTest` 下沉 4 个纯逻辑测试文件到 `core/commonTest`：

| 测试 | 测试数 | 主要契约 |
| --- | ---: | --- |
| `DtoSerializationTest` | 30 | DTO roundtrip、未知字段与兼容输入 |
| `PasteTypeTest` | 21 | 类型映射和边界值 |
| `CreatePasteItemHelperTest` | 28 | paste item 构造、hash、size |
| `MurmurHash3Test` | 8 | 跨平台 hash 稳定性 |
| 合计 | 87 | JVM、JS、host Native |

迁移暴露出 `core:desktopTest` 曾隐式依赖 app 提供 SLF4J。已仅在 `desktopTest` 增加 Logback runtime，使 core 能独立测试；没有向 common 依赖图加入 JVM 库。

其余 portable test 缺口随各业务批次补齐，R2-13 只做最终盘点，不再集中搬迁。

### R2-00-004 [P3] Resolved

app ktlint 不再排除全部 `desktopTest` 和任意 `/db/` 路径，仅保留 generated、vendored AndroidX 和 SQLDelight generated database 文件的精确排除。首批纳入门禁的 8 个 DB 测试文件及 `HikariSqliteDriver` 已格式化。

## 2. 验证

| 命令 | 结果 |
| --- | --- |
| `./gradlew :app:ktlintCheck` | PASS |
| `./gradlew :core:build --rerun-tasks --no-daemon --stacktrace` | PASS，desktop/JS/host Native 的 87 个迁移测试均执行 |
| `./gradlew :core:build :shared:build :app:build --no-daemon --stacktrace` | PASS，1m27s；145 tasks，包含 migration verification 和 app desktop tests |

## 3. 后续入口

R2-01 直接从 `core/src/commonMain` 的 API 和 serialization contract 开始。先复用 `core/src/commonTest` 已有 4 组测试，再补缺失契约；不要重新扫描 R2-00 的模块规模与 CI 基线。
