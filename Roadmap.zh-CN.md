# 项目路线图

以下是我们当前的项目路线图，概述了从当前版本到未来主要更新的计划功能和版本：

```mermaid
graph LR
    A[v1.0.6<br/>Current] --> B[v1.1.0]
    B --> C[v1.2.0]
    C --> D[v1.3.0]
    D --> E[v2.0.0]
    E --> F[v3.0.0]
    F --> G[v4.0.0]

    B -.- B1[支持颜色粘贴板]
    C -.- C1[支持原生粘贴板，改进粘贴板性能]
    D -.- D1[支持命令行模式]
    E -.- E1[支持插件系统]
    F -.- F1[实现 AI 等一系列官方插件]
    G -.- G2[支持一套键鼠控制多台设备]

    classDef current fill:#f9d5e5,stroke:#333,stroke-width:2px
    classDef minor fill:#eeac99,stroke:#333,stroke-width:2px
    classDef major fill:#e06377,stroke:#333,stroke-width:2px
    class A current
    class B,C,D minor
    class E,F,G major
```

**注意**： 此路线图代表了我们当前的开发计划和项目愿景。随着开发的进行，我们可能会根据社区反馈、技术进步和不断变化的优先级进行调整。我们欢迎社区参与和贡献！如果您对帮助塑造这个项目的未来感兴趣，请考虑加入我们的社区，为项目的成长贡献力量。

您的意见和贡献可以对项目的发展产生重大影响。无论是通过代码贡献、功能建议，还是帮助完善文档，都有多种方式可以参与其中。查看我们的[贡献指南](./Contributing.zh-CN.md)，了解更多关于如何参与的信息。

让我们一起努力，让这个项目变的更棒！
