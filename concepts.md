# Concepts

## Clipboard Collection and Clip Item

Every time you copy something, the software sets a series of data to the clipboard. This might include a single type or multiple types. For instance, copying plain text sets a single text type on the clipboard. Copying content from Word, however, sets multiple types: first, plain text data, then HTML data that records rich text information like Word font color. All these types exist on the system clipboard, and how they are used depends on the software you paste into. CrossPaste makes every effort to record all types of data on the system clipboard. We call this collection a **Clipboard Collection**, and each type within it is called a **Clip Item**.

### Priority of Clipboard Types

[The default priority](./composeApp/src/desktopMain/kotlin/com/crosspaste/paste/plugin/SortPlugin.kt) for previewing clipboard types is as follows:

| Priority | Clipboard Type |
|----------|----------------|
| 4        | File           |
| 3        | Image          |
| 2        | HTML           |
| 1        | URL            |
| 0        | Text           |
| -1       | Invalid        |