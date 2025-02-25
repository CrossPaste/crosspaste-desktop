---
outline: deep
---

# Concepts

## Pasteboard and Paste Items

1. Pasteboard

   For each copy operation, CrossPaste comprehensively captures the pasteboard content, not limited to a single data type.

2. Paste Item

   Paste items are the basic units that make up a pasteboard. Each paste item represents a specific type of data.

3. Diverse Data Collection

   A single copy operation may produce multiple paste items, forming a data collection. For example:

    - Copying web text: records both plain text and rich text (including font, color, and other style information)
    - Copying an image file: records both image data and file name

4. Intelligent Pasting

   Based on the pasting environment, CrossPaste automatically selects the most appropriate paste item. For instance:

    - When pasting an image into a text box, it intelligently uses the file name
    - When pasting into an application that supports rich text, it preserves the original format

### Design Philosophy
CrossPaste's core design goal is to maximize the preservation of information from the original copied content and provide the most suitable data when pasting based on the context. This approach ensures the best user experience across platforms and application scenarios.

## Priority of Paste Types

The [default priority](https://github.com/CrossPaste/crosspaste-desktop/blob/main/app/src/commonMain/kotlin/com/crosspaste/db/paste/PasteType.kt) for previewing paste types is as follows:

| Priority | Paste Type   |
|----------|--------------|
| 6        | File         |
| 5        | Color        |
| 4        | HTML         |
| 3        | RTF          |
| 2        | Image        |
| 1        | Link         |
| 0        | Text         |
| -1       | Invalid Type |