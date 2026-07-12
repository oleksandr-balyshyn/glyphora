# tui-core

Foundational types for the tui library — the maximum-stability tier everything else
builds on (`SPEC.md` §2). No dependencies, no terminal I/O, no reflection.

- **Geometry**: `Rect`, `Position`, `Size`.
- **Frame buffer**: `Buffer` (mutable cell grid, absolute coordinates, silent clipping),
  `Cell` (a `String` symbol, because one cell can hold a multi-codepoint grapheme cluster).
- **Styling**: `Style`, `Color`, `Modifiers` (allocation-free bitset).
- **Text**: `Text` / `Line` / `Span`.
- **`CharWidth`**: terminal display-width arithmetic (CJK, combining marks, emoji ZWJ
  sequences, flags, variation selectors). The wide-codepoint table is generated from the
  Unicode Character Database by `tools/generate-width-table.py`. **No code outside
  `CharWidth` may use `String.length`/`String.substring` for layout math.**
- **Layout**: `Constraint` (`Length`/`Percentage`/`Ratio`/`Min`/`Max`/`Fill`) and the
  `Layout.split` solver.
- **Widget traits**: `Widget`, `StatefulWidget[S]` — SAM-convertible.
- **Input events**: `Event` / `KeyEvent` / `MouseEvent` ADT (defined here, not in
  `tui-terminal`, so widgets stay backend-agnostic — `SPEC.md` §3.1).

## Example

```scala
import io.worxbend.tui.core.*

val buffer = Buffer(Rect(0, 0, 20, 3))
val areas = Layout.vertical(1, Constraint.fill).split(buffer.area)
buffer.setString(areas(0).x, areas(0).y, "Title", Style.Default.bold.withFg(Color.Cyan))
```
