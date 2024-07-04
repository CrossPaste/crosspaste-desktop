# Concepts

## Pasteboard Collection and Paste Item

Every time you copy something, the software sets a series of data to the pasteboard. This might include a single type or multiple types. For instance, copying plain text sets a single text type on the pasteboard. Copying content from Word, however, sets multiple types: first, plain text data, then HTML data that records rich text information like Word font color. All these types exist on the system pasteboard, and how they are used depends on the software you paste into. CrossPaste makes every effort to record all types of data on the system pasteboard. We call this collection a **Pasteboard Collection**, and each type within it is called a **Paste Item**.

### Priority of Pasteboard Types

[The default priority](./composeApp/src/desktopMain/kotlin/com/crosspaste/paste/plugin/SortPlugin.kt) for previewing pasteboard types is as follows:

| Priority | Pasteboard Type |
|----------|-----------------|
| 4        | File            |
| 3        | Image           |
| 2        | HTML            |
| 1        | URL             |
| 0        | Text            |
| -1       | Invalid         |