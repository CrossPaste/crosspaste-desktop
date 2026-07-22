# CrossPaste Desktop Review Index

> 本目录是 desktop 长期 review 的唯一进度入口。每个批次结束时必须更新本文件和对应报告；后续对话先读这里，避免重复审查。

## 当前基线

| 项目 | 值 |
| --- | --- |
| 基线日期 | 2026-07-22 |
| 仓库 | `crosspaste-desktop` |
| 分支 | `main` |
| HEAD | `c34366d9c28f7251b2ab07978589ed5689e5a903` |
| 工作区 | 建立基线时 clean |
| 当前批次 | R2-00 修复已完成，下一批为 R2-01 |
| 详细记录 | [R2-00-baseline.md](R2-00-baseline.md)、[R2-00-remediation.md](R2-00-remediation.md) |

`crosspaste-mobile` 由另一个 session 独立 review。本目录只审查 desktop 仓库；mobile 仅作为 `commonMain` 已有外部消费者这一契约背景，不记录其平台实现、CI 或业务问题。

## 批次进度

| ID | Desktop 范围 | 状态 | 记录 |
| --- | --- | --- | --- |
| R2-00 | 版本基线、模块、CI、测试资产、common 可复用门禁 | 完成 | [基线](R2-00-baseline.md) / [修复](R2-00-remediation.md) |
| R2-01 | `core/commonMain` API、模型、序列化基础与依赖边界 | 待开始 | - |
| R2-02 | `shared/commonMain` 数据、存储、配置与领域接口 | 待开始 | - |
| R2-03 | 协议 DTO、wire format 与跨版本兼容 | 待开始 | - |
| R2-04 | 身份、配对、凭据、加密与安全边界 | 待开始 | - |
| R2-05 | 网络发现、client/server、重试与异常恢复 | 待开始 | - |
| R2-06 | 同步状态机、一致性、并发与生命周期 | 待开始 | - |
| R2-07 | SQLDelight schema、迁移、DAO 与数据兼容 | 待开始 | - |
| R2-08 | Paste / Task 生命周期、幂等性和错误恢复 | 待开始 | - |
| R2-09 | 文件、图片、压缩、资源与路径安全 | 待开始 | - |
| R2-10 | 配置、启动、后台任务与资源清理 | 待开始 | - |
| R2-11 | `desktopMain` 平台实现及 common/desktop 分层 | 待开始 | - |
| R2-12A | ViewModel、UI 状态与导航 | 待开始 | - |
| R2-12B | Compose UI 与桌面交互 | 待开始 | - |
| R2-13 | 测试架构、portable tests 与覆盖缺口 | 待开始 | - |
| R2-14 | 性能、内存、I/O、网络与启动 profile | 待开始 | - |
| R2-15 | 重构收敛、最终回归和发布门禁 | 待开始 | - |

## 问题台账

| ID | 级别 | 摘要 | 状态 | 目标批次 |
| --- | --- | --- | --- | --- |
| R2-00-001 | P1 | CI 的 `app:build` 未执行 `core/shared` 全目标构建及 migration verification | Resolved | R2-00 修复 |
| R2-00-002 | P1 | `app/commonMain` 仅有 JVM target，却承担主要跨项目复用源码 | In progress | R2-01 / R2-02 |
| R2-00-003 | P2 | 34K common 源码几乎没有 portable common tests | In progress | 各业务批次 / R2-13 汇总 |
| R2-00-004 | P3 | app ktlint 排除全部 `desktopTest` 与任意 `/db/` 路径 | Resolved | R2-00 修复 |

## 续审规则

1. 开始新对话时先读本文件和上一批报告，再核对 HEAD、分支和工作区。
2. HEAD 未变化时，不重复已完成的目录扫描、行数统计和基线构建；只处理未关闭问题与下一批范围。
3. HEAD 已变化时记录增量 diff。只有增量触及已审目录或契约时，才重开对应结论。
4. 每个发现使用稳定 ID，记录级别、文件/行号证据、影响、修复建议、验证条件和状态。
5. 每批结束更新“当前基线”“批次进度”“问题台账”，并写明下一批精确入口。
6. desktop 与 mobile 问题不混用编号；需要跨仓协调时，只在 desktop 记录接口/兼容契约及对方 session 的报告链接或 commit。

## 下一步入口

R2-01 从 `core/src/commonMain` 的 62 个文件开始，逐文件建立 API 与序列化契约清单，重点检查：

- public/internal API 的稳定性、可变性、默认值和错误语义。
- `@Serializable` 类型的字段演进、未知字段、缺失字段和旧版本兼容。
- 是否混入 UI、平台、数据库或网络实现细节。
- desktop、JS、Native target 的 actual/provider 差异。
- 哪些测试应从 `app/src/desktopTest` 下沉到 `core/src/commonTest`。

已迁移的 DTO serialization、PasteType、PasteItem helper 和 MurmurHash3 测试不要重复审查；直接以 `core/src/commonTest` 为入口检查测试缺口。
