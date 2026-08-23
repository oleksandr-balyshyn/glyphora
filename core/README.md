# tui-core

Foundational types for the tui library — the maximum-stability tier everything else
builds on. No dependencies, no terminal I/O, no reflection.

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
- **`Measured`**: the one contract for "how much space does this content need" —
  `heightAt(width)` / `widthAt(height)`, both `Option`, where `None` means *cannot say*
  and must never be read as a size. Widgets that know their own content (`Paragraph`,
  `Markdown`, `Notice`, `Badge`, `Spinner`, `BigText`, `AnimatedText`, `Tooltip`) mix it in, and the
  DSL's measurement pass asks through it instead of per-widget ad-hoc methods.
- **Input events**: `Event` / `KeyEvent` / `MouseEvent` ADT (defined here, not in
  `tui-terminal`, so widgets stay backend-agnostic), plus `KeyEvent.parse` — the one
  reader of the `"ctrl+s"` / `"shift+tab"` / `"f2"` key-spec vocabulary. It lives here
  so an app's `binding("ctrl+s", …)`, the documented key names, and `Pilot.press("ctrl+s")`
  in a test all go through the same parser instead of three hand-kept translations.
- **Motion**: `Progress` — the one answer to "where is this animation at `elapsed`",
  either as a one-shot fraction (`normalized`, what `Tween` and the timed `Effect`s
  ease) or as a whole position in a repeating cycle (`stepped`/`steppedAtRate`, what
  the animated widgets use) — plus `Easing`, `Tween`, `Spring`, and `Effect`, the
  post-render frame transform. Every one of them is a pure function of a time it is
  handed: no clock, no thread, no terminal, which is why they belong down here rather
  than in `tui-runtime`, where `tui-widgets` could not reach them.

## Example

```scala
import io.worxbend.tui.core.*

val buffer = Buffer(Rect(0, 0, 20, 3))
// split2/split3/split4/split5 destructure a known arity into a tuple; `split` returns a Seq
val (titleArea, body) = Layout.vertical(1, Constraint.fill).split2(buffer.area)
buffer.setString(titleArea.x, titleArea.y, "Title", Style.Default.bold.withFg(Color.Cyan))
buffer.setString(body.x, body.y, "Body", Style.Default)
```
