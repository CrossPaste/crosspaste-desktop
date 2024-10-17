# Project Roadmap

Below is our current project roadmap, outlining the planned features and versions from the current release to future major updates:

```mermaid
graph LR
    A[v1.0.11<br/>Current] --> AA[v1.0.12]
    AA --> AB[v1.0.13]
    AB --> B[v1.1.0]
    B --> C[v1.2.0]
    C --> D[v1.3.0]
    D --> E[v2.0.0]
    E --> F[v3.0.0]
    F --> G[v4.0.0]

    AA -.- AA1[Support RTF format in clipboard]
    AB -.- AB1[Support color data in clipboard]
    B -.- B1[Support for mobile devices and shared clipboard with mobile]
    C -.- C1[Support native pasteboard, improve pasteboard performance]
    D -.- D1[Support command-line mode]
    E -.- E1[Support plugin system]
    F -.- F1[Implement AI and other official plugins]
    G -.- G2[Support controlling multiple devices with one set of keyboard and mouse]

    classDef current fill:#f9d5e5,stroke:#333,stroke-width:2px
    classDef minor fill:#eeac99,stroke:#333,stroke-width:2px
    classDef major fill:#e06377,stroke:#333,stroke-width:2px
    class A current
    class AA,AB,B,C,D minor
    class E,F,G major
```

**Note**: This roadmap represents our current development plans and vision for the project. As development progresses, adjustments may be made based on community feedback, technological advancements, and changing priorities. We welcome community involvement and contributions! If you're interested in helping shape the future of this project, please consider joining our community and contributing to its growth.

Your input and contributions can make a significant impact on the project's development. Whether it's through code contributions, feature suggestions, or helping with documentation, there are many ways to get involved. Check out our [Contributing Guidelines](Contributing.md) to learn more about how you can participate.

Together, we can build something amazing!