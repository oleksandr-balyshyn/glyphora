---
title: Widget catalog
description: Choose, compose, configure, and test glyphora's layout, content, input, data, visualization, and feedback widgets.
---

# Widget catalog

glyphora ships more than fifty backend-agnostic widgets. The high-level DSL wraps
each widget in an `Element`, adds focus and input behavior where appropriate, and
keeps mutable interaction state owned by your application.

Use this page to choose a widget and understand its state model. The
[Scaladoc API](pathname:///api/widgets/) remains the exact-signature reference.

## Choose by job

| Job | Start with | Add when needed |
|---|---|---|
| Arrange a screen | `row`, `column`, `panel`, `spacer`, `rule` | `scrollView`, `splitPane`, `layers` |
| Show prose or records | `text`, `list`, `table`, `tabs` | `markdown`, `SyntaxHighlighter`, `log`, `dataTable`, `directoryTree` |
| Collect input | `input`, `checkbox`, `toggle`, `select`, `button` | `radioGroup`, `slider`, `textArea`, `autocomplete`, `filePicker`, `selectionList`, `Form` |
| Show a metric | `gauge`, `sparkline` | `lineGauge`, `dualSparkline`, `barChart` |
| Plot data | `chart`, `pieChart`, `heatmap` | `stackedBarChart`, `canvas`, `calendar` |
| Communicate progress | `spinner`, `progressBar`, `orbitSpinner`, `skeleton`, `indeterminateBar` | `marquee`, `animatedText`, effects, toasts |
| Report an outcome | `notice`, `badge` | toasts, dialogs |
| Structure an app | `scaffold`, `topBar`, `sidebar`, `statusBar` | screens, menus, command palette, dialogs, `paginator` |

## The state ownership rule

Interactive widgets do not hide application state. You create the state once,
retain it across renders, and pass it into the element factory:

```scala
import io.worxbend.tui.dsl.*

private val name = TextInputState()
private val notifications = Signal(true)
private val environment = Signal(0)

def settings(using ReactiveScope): Element =
  column(
    text("Project name").dim,
    input(name, placeholder = "glyphora-app"),
    toggle("Notifications", notifications),
    select(Seq("development", "staging", "production"), environment),
    button("Save") { save(name.value, notifications.peek, environment.peek) },
  ).gap(1)
```

The focused widget's built-in key handler mutates its state and requests a redraw.
Signals add tracked reactivity where another part of the tree also needs the value.

> Construct widget state **outside** `view`. Creating `TextInputState()`,
> `ListState()`, or `DataTableState()` inside `view` would reset editing, selection,
> and scrolling on every redraw.

## One name for the chosen thing

Every widget that draws one of its items differently because it is the chosen one
calls that parameter `highlightStyle` — `ListView`, `Tree`, `Menu`, `DataTable`,
`DirectoryTree`, `Tabs`, `Calendar`, `Dialog`, `RadioGroup` and `Paginator` alike.
Learn it once and it transfers.

The DSL fills it in for you from the app theme's `focus` style, so a `list` and a
`menu` in the same app highlight identically and switching to `Theme.HighContrast`
moves every one of them at once. You only pass it by hand when you build the widget
value yourself — `DataTable` is the common case, because its sort, filter and paging
options mean you construct it rather than let a factory do it:

```scala
DataTable(rows, columns, highlightStyle = theme.focus)
```

## Layout and chrome

Core structural elements:

- `panel(title)(children*)` / `panel(children*)` — bordered vertical container;
- `row(children*)` / `column(children*)` — constrained layout containers;
- `spacer` / `spacer(cells)` — flexible or fixed blank space;
- `line(spans*)` — one row carrying several styles (see [Text, documents, and logs](#text-documents-and-logs));
- `rule(label)` — horizontal divider;
- `scrollView(content, state)` — measured vertical viewport with wheel/key scrolling;
- `tabbedContent("Name" -> page, ...)(selected)` — tabs plus the selected page;
- `collapsible(title, expanded)(body)` — toggleable disclosure region;
- `splitPane(first, second, splitPercent)` — keyboard/mouse-resizable panes;
- `layers(base, overlays*)` — paint later elements over earlier ones;
- `positioned(dx, dy, width, height)(content)` — place content at an exact offset
  inside the area, clipped to it (see
  [Overlay at an exact offset](./layout-and-style#overlay-at-an-exact-offset));
- `menu`, `tooltip`, and `dialog` — transient interaction surfaces. A `tooltip` sizes
  itself to its text and has no anchor of its own, so it is `positioned` over a
  `layers` base at the cell it should point at.

`scrollView` draws its own scrollbar. For the rarer case of a scroll indicator beside
something the DSL is not scrolling — a custom render loop, a pane whose offset your own
code owns — reach past the DSL for the raw `Scrollbar` widget. It has no DSL element and
owns no state: where the thumb sits is a pure function of the two numbers you pass, so
the offset stays wherever you already keep it.

```scala
import io.worxbend.tui.core.Direction
import io.worxbend.tui.widgets.Scrollbar

// 200 rows of content, currently showing from row 40
widget(Scrollbar(contentLength = 200, position = 40)).rows(1)

// along the bottom edge instead
Scrollbar(200, 40, orientation = Direction.Horizontal)
```

The thumb's length is proportional to how much of the content the track covers, and a
`position` past the end pins it to the end rather than drawing it off the track. When the
content fits, only the track is drawn.

### Panel padding and captions

A panel reserves blank cells between its border and its children with `.padding(cells)`
or `.padded(Padding(...))`, and can carry a caption on each border:

```scala
panel("Errors")(errorList)
  .padding(1)                       // 1 row top and bottom, 2 columns each side
  .titleBottom(s"${errors.size} total")
  .titleStyle(_.withFg(Color.Red))  // the caption reddens; the frame keeps its own style
```

`.padding(1)` is *not* one cell on all four sides. A terminal cell is roughly twice as
tall as it is wide, so one blank row costs about twice as much of the screen as one blank
column; `.padding(n)` therefore reserves `n` rows above and below and `2 * n` columns
either side, which is what reads as an even margin. When you want the exact counts,
`.padded(Padding(left = 4, right = 1, top = 0, bottom = 0))` sets each side on its own,
and `Padding.uniform`, `Padding.horizontal`, `Padding.vertical` and `Padding.symmetric`
name the common shapes. `Padding.left`, `Padding.right`, `Padding.top` and
`Padding.bottom` pad a single side.

`Block`'s `borders` is a bitset of the sides that draw a line. `Borders.All`,
`Borders.None` and the four single sides combine with `|`; `Borders.Horizontal` is the
top and bottom edges and `Borders.Vertical` the left and right ones. To take a side away
rather than list the ones that stay, use `without`:

```scala
Block(borders = Borders.All.without(Borders.Top))   // same as Right | Bottom | Left
```

`&` intersects two sets, `hasAny` asks whether any named side is drawn, `hasAll` whether
every one of them is, and `show` prints the set as `"Top|Left"` instead of a raw number.

`titleBottom` writes into the bottom border at the right, `title` into the top border at
the left. Neither costs a content row: they overwrite border cells that were being drawn
anyway. Below the DSL, `Block` takes a `Seq[BlockTitle]` and any number of them can share
a border — `BlockTitle.top(line, Alignment.Center)`, `BlockTitle.bottom(line)`, and so on.

`Block` has two styles and they do different jobs. `borderStyle` colours the frame glyphs
and the titles. `style` paints the *whole* area — frame and interior together — before
anything is drawn, which is how a panel gets a background colour distinct from the screen
behind it:

```scala
Block(
  Seq(BlockTitle.top(Line.raw("Errors"))),
  style = Style.Default.withBg(Color.Blue),        // the panel's own background
  borderStyle = Style.Default.withFg(Color.Red),   // layered on top, so the frame keeps that background
)
```

The fill changes the style of each cell and never its glyph, so content already drawn in
that area keeps its text and its foreground colour — you can render the block before or
after the content and get the same frame. `borderStyle` is layered on `style` rather than
replacing it, so it only has to say what is *different* about the frame; a block left at
the default `style` paints no fill at all.

### Border sets

`borderType` picks the glyphs the frame is drawn from. Every built-in set is one terminal
column wide in every position, so switching between them never moves `inner` — the frame
changes, the content area does not.

| `BorderType` | Looks like | Use it for |
|---|---|---|
| `Plain`, `Rounded`, `Double`, `Thick` | `┌─┐` `╭─╮` `╔═╗` `┏━┓` | the four classic box-drawing weights |
| `Ascii` | `+--+` | terminals and fonts with no box-drawing glyphs, and output that gets pasted somewhere that is not a terminal |
| `LightDoubleDashed`, `LightTripleDashed`, `LightQuadrupleDashed` | `┌╌╌┐` `┌┄┄┐` `┌┈┈┐` | a frame that reads as provisional or inactive next to a solid one |
| `HeavyDoubleDashed`, `HeavyTripleDashed`, `HeavyQuadrupleDashed` | `┏╍╍┓` `┏┅┅┓` `┏┉┉┓` | the same, at the thick weight |
| `QuadrantOutside`, `QuadrantInside` | `▛▀▜` / `▗▄▖` | half-cell frames — one sits half a cell outside the area, the other half a cell inside, so two nested blocks meet with no gap |
| `OneEighthWide`, `OneEighthTall` | `▁▁▁` / `▕▔▏` | the "McGugan box": one-eighth blocks pressed against the cell edge, which reads as a hairline |
| `ProportionalWide`, `ProportionalTall` | `▄▄▄` / `█▀█` | a frame that looks the same thickness all round, compensating for a cell being about twice as tall as it is wide |
| `Full` | `███` | a solid slab |
| `Blank` | spaces | a border-styled margin, or keeping a title's border row with no frame under it |

The dashed sets keep the solid corners of their weight, because a dashed corner glyph does
not exist and the eye only reads a broken run of dashes as a line when its ends are pinned
down.

When none of those is the frame you want, `borderSet` takes the eight glyphs directly and
wins over `borderType`:

```scala
Block(borderSet = Some(BorderGlyphs.symmetric("─", "│", "┌", "┐", "└", "┘")))
Block(borderSet = Some(BorderGlyphs.uniform("*")))
```

`BorderGlyphs` keeps the two sides of each axis apart — `verticalLeft`/`verticalRight` and
`horizontalTop`/`horizontalBottom` — because the half-cell sets are not symmetric: the left
edge of `QuadrantOutside` is `▌` and its right edge is `▐`. `BorderGlyphs.symmetric` is the
short spelling when both sides of an axis share a glyph, and `BorderGlyphs.uniform` when all
eight do.

### Drop shadows

A `shadow` makes a dialog or a popup read as floating above the screen rather than cut into
it:

```scala
Block(shadow = Some(Shadow.Default))                       // one cell down and right, dimming what is behind
Block(shadow = Some(Shadow.shade(ShadowFill.MediumShade))) // painted with ▒ instead
Block(shadow = Some(Shadow.dim(2, 1)))                     // a longer shadow to the right than below
```

Unlike a CSS box shadow, which spills outside its element, this one is paid for *inside* the
block's own area — a widget in this toolkit never draws outside the rectangle it is handed.
A block with the default one-cell shadow frames itself one column narrower and one row
shorter, the freed strip becomes the shadow, and `inner` shrinks to match, so a layout never
gets a surprise. A negative offset casts up and to the left instead, and the frame moves down
and right to make the room there. On an area too small to leave a usable frame behind, the
shadow is dropped and the block renders as if it had none.

There are two ways to paint the band, and which one you want depends on the terminal.
`ShadowFill.Dim` keeps the glyphs that were already there and only dims them, so text behind
the panel stays legible while visibly receding — but a good many terminals render the dim
attribute as nothing at all. `ShadowFill.LightShade`, `MediumShade`, `DarkShade` and `Solid`
overwrite the band with `░`, `▒`, `▓` and `█`, which works everywhere; `ShadowFill.Symbol`
takes a single-column glyph of your own.

See [Layout & style](./layout-and-style) for constraints and [The app shell](./app-shell)
for application-level composition.

## Text, documents, and logs

```scala
column(
  text("Deployment complete").bold.fg(Color.Green),
  text("8 services updated · 0 failed").dim,
  rule("release notes"),
  markdown(releaseNotes),
)
```

- `text` uses a grapheme-aware paragraph renderer, in one style for the whole block.
- `line(spans*)` is the mixed-style row: each part carries its own style.
- `markdown` supports headings, lists, quotes, fenced code, inline styles, and OSC 8
  links. Its DSL element reports width-dependent height to scroll containers.
- `log(LogState)` supports append-heavy output and follow-tail behavior.
- `link(label, url)` emits a clickable OSC 8 hyperlink when the terminal supports it.
- `bigText` renders banners; `image` renders raster data with half-block cells.

### How wrapping breaks a line

`Overflow.Wrap` — the mode `Paragraph`, `markdown` and `notice(...).wrapped` use to grow
onto further rows instead of cutting text off — breaks between words:

| Input, at width 8 | Rows drawn |
| --- | --- |
| `hello world` | `hello` / `world` — the word moves whole, it is not cut after `hello wo` |
| `aa    bb` (four blanks) | `aa` / `bb` — the blanks sat at the break, so they are dropped |
| `    indented text` | `    indented` / `text` — blanks at the *start* of a line are content, not a break point |
| `supercalifragilistic` | `supercal` / `ifragili` / `stic` — a word longer than the whole width has nowhere to go |

Two Unicode characters exist purely to control this, and both are honoured:

- `U+00A0` NO-BREAK SPACE draws a blank column but forbids a break, so `10 kg` never ends
  up with the number alone at the end of a row.
- `U+200B` ZERO WIDTH SPACE draws nothing but permits one, which is how Thai, Japanese and
  long URLs mark a place a wrapper may break.

The classification lives in `io.worxbend.tui.core.LineBreaks` (`isBreakingSpace`,
`isZeroWidthBreak`, `endsWithZeroWidthBreak`) next to `CharWidth`, so any code that wraps
text agrees with the built-in widgets about where a break is allowed.

`heightAt(width)` counts exactly the rows this produces, so a scroll container or a
layout pass sizing a wrapped paragraph never disagrees with what is drawn.

### Which end of a too-wide line survives

A line that still does not fit — because the paragraph clips rather than wraps, or
because it is one unbreakable word — loses the side *away* from its alignment:

| Alignment | `"/var/log/app.log"` in seven columns |
| --- | --- |
| `Left` | `/var/lo` — the beginning, as before |
| `Right` | `app.log` — the end, the part the alignment was chosen to show |
| `Center` | `og/app.` — as much goes from each side |

A cut that lands inside a wide character drops the whole character rather than half
of it, so the row can start one column in from the edge instead of showing a broken
glyph. `CharWidth.dropByWidth(text, columns)` is the counterpart of
`substringByWidth` this is built on, for custom widgets that need the same thing.

### One row, several styles

`text(...)` paints its whole block in a single style, so it cannot say `Status:` in the
default colour and `OK` in green. `line(...)` can:

```scala
line("Status: ", "OK".styled(_.withFg(Color.Green)))
```

Each part is either a plain `String` or a `Span`, and the two mix freely in one call.
`"...".styled(transform)` builds a `Span` — a run of text with its own style. A part
written as a bare `String` carries no style of its own, so it is drawn in the element's
style; that is why the label above needs no `.styled(identity)` to sit beside a coloured
span. The element's own style is the base each span layers onto, so `line(...).dim` dims
the whole row and a span that set its own colour keeps it.

A `line` can also place itself: `line(...).rightAligned` (and `leftAligned`, `centered`,
`aligned(Alignment.Center)`) sets the alignment on the underlying `Line`, which wins over
the alignment the paragraph drawing it was given. Stack them in a `column` to get a
left-aligned heading above right-aligned figures without a second widget. See
[Layout and style](./layout-and-style.md#align-one-row-of-text-on-its-own).

The alternative before `line` existed was a `row` of `text` elements with hand-counted
widths: `row(text("Status: ").length(8), text("OK").fg(Color.Green))`. Do not do that.
The `8` is a display width written by hand, and it is wrong the moment the label is
translated, and wrong today for any CJK or emoji text, where one character occupies two
terminal columns. `line` measures its parts through `CharWidth`, so the row stays correct.

`SyntaxHighlighter` turns source code into styled `Text`, which any text-taking widget
or element can then render. It is a pure function, not a widget, so it owns no state:

```scala
import io.worxbend.tui.widgets.{Language, SyntaxHighlighter, SyntaxTheme}

text(SyntaxHighlighter.highlight(snippet, Language.Scala))

// `Language.of` resolves a Markdown fence's info-string; unknown names fall back to Generic
text(SyntaxHighlighter.highlight(snippet, Language.of("sh"), SyntaxTheme(comment = Style.Default.dim)))
```

Highlighting is line-oriented and dependency-free: it recognises line comments,
single-line strings, numbers, per-language keywords, `name(` call sites and shell
`$variables`. Block comments and triple-quoted strings that span lines fall back to plain
text — the right trade-off for snippets in help screens, READMEs and Markdown fences
rather than for a full editor. `markdown` already highlights its fenced code this way.

## Lists and navigation

```scala
import io.worxbend.tui.widgets.ListState

private val selected = ListState()
private val services = Signal(Vector("api", "worker", "scheduler"))

def serviceList(using ReactiveScope, Theme): Element =
  list(services.get, selected).onKey(Key.char('d')) {
    selected.selected.foreach { index =>
      services.update(_.patch(index, Nil, 1))
      selected.selected = None
    }
  }
```

While a list is focused, Up and Down move the highlight one row, Home and End jump to
the first and last item, and PageUp/PageDown move it ten rows at a time. Those four
jumps are also available on the state object as `selectFirst`, `selectLast` and
`selectBy` (which takes a signed number of rows), so an app can bind its own keys to
them. None of them touches the scroll offset: the list re-derives that during the next
render, which is what scrolls the chosen row into view. A `dataTable` gains the
same four moves, except that a table with paging turned on keeps PageUp/PageDown for
turning pages.

By default the highlight is allowed to come to rest on the very first or very last
visible row, with more items sitting just out of sight below it — the reader cannot see
what is coming next. `ListState(scrollPadding = 2)` keeps two further items visible on
each side of the highlight whenever the list is long enough to show them, so pressing
Down two rows short of the bottom scrolls the list underneath a highlight that stays put.
Near the two ends of the list the padding gives way, because there is nothing left to
reveal: the first and last items still reach the edge rows.

A list normally starts at the top row of its area and grows downward, so a list with
three entries in a ten-row pane leaves seven blank rows underneath it. A chat transcript
or a log tail wants the opposite: the newest entry welded to the bottom edge, with the
blank rows above it. Call `bottomToTop` for that. It anchors the first item of the
sequence to the *last* row of the area and grows upward, so feed the items newest-first.
The underlying `ListView` takes the same choice as a `ListDirection` value
(`TopToBottom`, the default, or `BottomToTop`); nothing else changes — the selection
still clamps and scrolls exactly as it does the other way up.

```scala
private val transcript = ListState()

def chatPane(using ReactiveScope, Theme): Element =
  list(messages.get.reverse, transcript).bottomToTop
```

Use `tree(nodes, TreeState)` for in-memory hierarchy and
`directoryTree(DirectoryTreeState(root))` for the filesystem. The directory tree
loads branches lazily, caches listings, and exposes `invalidate()` when outside code
changes a directory.

```scala
import io.worxbend.tui.widgets.DirectoryTreeState
import java.nio.file.Paths

private val files = DirectoryTreeState(Paths.get("."))

def browser: Element =
  panel("Files")(directoryTree(files)).rounded
```

## Tables: simple and interactive

Use `table` for static rows:

```scala
table(
  Seq(
    Seq("api", "ready", "3"),
    Seq("worker", "scaling", "8"),
  ),
  Constraint.Length(18),
  Constraint.Fill(1),
  Constraint.Length(6),
)
```

Passing no widths at all is allowed and means "equal columns" — `table(rows)` divides
the area between as many columns as the widest visible row has, which is the quickest
way to get a grid on screen before deciding what each column deserves.

`.header(...)` adds a bold caption row above the data — one label per column, in the
same order as the widths. It costs one row of the area:

```scala
table(rows, Constraint.Length(18), Constraint.Fill(1), Constraint.Length(6))
  .header("Service", "Status", "Replicas")
```

One step below the DSL, a `Table`'s rows accept either a bare `Seq[Line]` — one cell
per column, one terminal line tall, which is all a row could be until now — or a
`TableRow`, and the two can be mixed in the same table:

```scala
import io.worxbend.tui.widgets.{Table, TableRow}

Table(
  rows = Seq(
    TableRow(Seq(Line.raw("api"), Line.raw("ready")), bottomMargin = 1),
    Seq(Line.raw("worker"), Line.raw("scaling")),
  ),
  widths = Seq(Constraint.Length(10), Constraint.Fill(1)),
)
```

A cell is likewise either a bare `Line`, covering one column, or a `TableCell`, which
covers several. That is how a grouped header is drawn — two captions, each centred over
its own pair of data columns:

```scala
Table(
  rows = data,
  widths = Seq.fill(4)(Constraint.Length(8)),
  header = Some(Seq(TableCell(Line.raw("inbound"), 2), TableCell(Line.raw("egress"), 2))),
)
```

A span covers the `columnSpacing` gaps between the columns it merges, so it is one
continuous run rather than several cells with holes, and it pushes the cells after it
along instead of overwriting them. It is clamped to at least one column and to no more
than the columns left in the row, so a span that ran off the end clips rather than
drawing outside the table. Spans are a `Table` feature; `DataTable`'s cells are plain
strings and stay one column each.

`TableRow` carries a `height`, a `topMargin`, a `bottomMargin`, and an optional per-row
`style` that layers over the table's own. The height and margins reserve vertical room —
a blank line under a group, breathing space in a sparse table — they do not wrap text,
because a cell is a single `Line`; the cells are drawn on the first line of the height
and the rest is left blank. A height below one clamps to one, and negative margins clamp
to zero, so a row always occupies at least the line it is drawn on.

`.footer(...)` adds a bold summary row — totals, a record count — pinned to the
*bottom* of the table's area rather than appended after the last data row:

```scala
table(rows, Constraint.Length(18), Constraint.Length(9))
  .header("Service", "Replicas")
  .footer("Total", replicas.sum.toString)
```

Pinning is the point. A totals row that simply followed the last row would float in the
middle of any pane the rows do not fill. It is laid out on the same solved column widths
as the body, which a second `table` stacked underneath could not promise — that one had
to repeat the width constraints and drifted the moment they changed. Like the header, it
costs one row of the area; `DataTable` takes the same `footer` and `footerStyle`, and
gives up one row of its scrollable body for it.

A `table` is a flex container, like `row` and `column`: `.gap(n)` sets the blank
columns between neighbouring cells, and `.center` / `.flexEnd` / `.spaceBetween` place
the block of columns inside the area when fixed widths leave space over. Without one of
those the leftover always trails off the right-hand side.

```scala
table(rows, Constraint.Length(18), Constraint.Length(9)).center.gap(2)
```

The same knob exists one level down on the widgets, as `Table(…, flex = Flex.Center)`
and `DataTable(…, flex = Flex.Center)`. It changes nothing when a `Fill` or `Min` column
is already absorbing the slack, because then there is no leftover to place.

Use `DataTable` when users need sorting, filtering, selection, scrolling, or paging:

```scala
import io.worxbend.tui.widgets.{DataTable, DataTableState}

private val tableState = DataTableState()
private val deployments = DataTable(
  columns = Seq("Service", "Status", "Replicas"),
  rows = Seq(
    Seq("api", "ready", "3"),
    Seq("worker", "scaling", "8"),
  ),
  widths = Seq(Constraint.Fill(2), Constraint.Fill(1), Constraint.Length(8)),
)

def tableView: Element = dataTable(deployments, tableState)
```

`tableState.selected` indexes `deployments.visibleRows(tableState)`, not the original
unsorted data. Use that method when opening the selected record.

`DataTableState` also carries a `selectedColumn`, the horizontal half of a
spreadsheet-style cursor. It is independent of `selected`: a column on its own
highlights a whole column, a row on its own highlights a whole row, and the two
together identify one cell. `selectNextColumn(n)` and `selectPreviousColumn(n)` walk it
and clamp at the ends the same way row selection does, and `selectCell(row, column)`
moves both halves at once.

Nothing is drawn for a column cursor unless the widget is given a style for it, so a
table that only selects rows behaves exactly as before:

```scala
DataTable(
  columns, rows, widths,
  columnHighlightStyle = Some(theme.focus),
  cellHighlightStyle = Some(theme.focus.bold),
)
```

The three styles are layered in that order — row, then column, then the cell where the
two cross — which is what lets the crossing cell look like neither of them.

By default the selected row is marked only by `highlightStyle`, which reverses its
foreground and background. Two kinds of terminal do not show that: one that ignores
reverse video, and one where the row already carries a background colour the reversal
blends into. `highlightSymbol` adds a text marker instead, the same way `ListView` does:

```scala
DataTable(columns, rows, widths, highlightSymbol = "> ")
```

The symbol's display width is reserved as a gutter on *every* row, header included, so
the columns do not shift sideways as the selection moves. The default is `""`, which
reserves nothing and draws exactly the grid a table written before this option existed
drew. The width is measured in terminal columns, not characters, so a two-column CJK
glyph reserves two.

## Text editing

| Widget | State | Notes |
|---|---|---|
| `input` | `TextInputState` | one line, horizontal scrolling, paste folds newlines |
| `textArea` | `TextAreaState` | multiple lines, 2D cursor, scrolling, bounded undo/redo |
| `numberInput` | `TextInputState` | whole numbers only; add `.decimal` to accept one decimal point |
| `maskedInput` | `TextInputState` | shows a mask rather than raw content |
| `autocomplete` | `AutocompleteState` | input plus selectable suggestions and accept callback; `.maxSuggestions(n)` caps the list |
| `filePicker` | `FilePickerState` | navigable file selection |

All editing and cursor movement is grapheme-cluster-aware. A Backspace removes one
visible cluster instead of one UTF-16 code unit. Internally both fields measure, scroll,
and draw a row through one shared rule (`ClusterRow`, package-private to `tui-widgets`):
every cluster occupies at least one cell, a cluster that would only half-fit at the right
edge is dropped rather than split, and a zero-width cluster is drawn as a blank. That is
why a wide (CJK) or emoji cluster never drifts the cursor a column further off-screen with
each keystroke — the arithmetic the scroll solver counts in is the same arithmetic the
draw loop advances by, because it is the same code.

## Choices, values, and paging

Three small controls that all keep their value where you already keep it — in a `Signal`
you own — rather than in a state object of their own:

```scala
import io.worxbend.tui.dsl.*

private val environment = Signal(0)
private val volume      = Signal(40)
private val page        = Signal(0)

def controls(using ReactiveScope): Element =
  column(
    radioGroup(Seq("development", "staging", "production"), environment),
    slider(volume, SliderRange.of(min = 0, max = 100, step = 5)),
    paginator(page, total = 12),
  ).gap(1)
```

| Element | Draws | Keys |
|---|---|---|
| `radioGroup(options, selected)` | one row per option, `(•)` beside the chosen one and `( )` beside the rest | Up/Down move the selection, which *is* the choice — there is no separate confirm step |
| `slider(value, range)` | `├───●──────┤` sized to its area | Left/Right move by `range.step`, Home/End jump to the ends; clicking or dragging the track jumps to that position |
| `paginator(current, total)` | dots (`● ○ ○`) while they fit, `page/total` otherwise | Left/Right change page |

Each element writes the `Signal` back when the user changes it, so anything else reading
that signal repaints. Values outside the range are clamped rather than drawn off the
control: a slider at `500` on a `0..100` range sits at the right-hand end, and a paginator
asked for page 99 of 3 marks the last dot. A slider's bounds and its step travel together
in a `SliderRange` — `SliderRange.Percent` is the default, and `SliderRange.of(min, max,
step)` builds any other. `of` orders the bounds either way round and rejects a step below
1, because a slider consumes Left/Right whether or not it can move: a zero step would
swallow both arrows, do nothing, and stop them reaching your own bindings. `paginator` displays pages 1-based while
`current` counts from 0, so page 1 of 12 renders as `1/12`.

A slider needs at least three columns (two brackets and one track cell) and draws nothing
below that; at exactly three, every value sits on the single track column. `paginator`
falls back to the `page/total` counter as soon as the dots would not fit, which for
`total` pages needs `total * 2 - 1` columns, and always uses the counter above ten pages.

Underneath, the raw `w.Slider`, `w.RadioGroup` and `w.Paginator` widgets take plain
values rather than signals, for use outside the DSL.

## Data visualization

A tick-driven dashboard can remain compact:

```scala
import scala.concurrent.duration.*

override def config = RunnerConfig(tickRate = Some(100.millis))
private val tick = Signal(0)
override def onTick(): Unit = tick.update(_ + 1)

def dashboard(using ReactiveScope, Theme): Element =
  val t = tick.get
  val load = (math.sin(t * 0.1) + 1) / 2
  val samples = Vector.tabulate(40)(i =>
    (math.sin((t + i) * 0.25) * 40 + 50).toLong
  )
  val wave = Vector.tabulate(80)(i =>
    (i.toDouble, math.sin((t + i) * 0.1) * 40 + 50)
  )

  column(
    row(
      panel("Load")(gauge(load)).percent(40),
      panel("Throughput")(sparkline(samples)).fill,
    ).length(4),
    panel("Signal")(
      chart(
        Seq(Dataset("wave", wave, graphType = GraphType.Line)),
        xBounds = (0.0, 80.0),
        yBounds = (0.0, 100.0),
      )
    ).fill,
  )
```

A `sparkline` scales to the largest value it was handed, so it always uses the full row
height — which also means two of them side by side are *not* comparable. `.max(n)` pins
the top of the scale, and two sparklines pinned to the same ceiling can be read against
each other:

```scala
row(
  panel("Ingress")(sparkline(inbound).max(1000L)).fill,
  panel("Egress")(sparkline(outbound).max(1000L)).fill,
)
```

### Bar glyphs

A terminal has no pixels, so a bar two and a half cells tall is drawn as two full cells and
one half-filled one. The glyphs that make that possible are the eight Unicode block
elements `▁▂▃▄▅▆▇█`, and `BarSet` is the record that holds them — one glyph per eighth of a
cell, plus an optional glyph for the cells the bar does not reach. `Sparkline` and
`BarChart` both take one as `barSet`:

```scala
Sparkline(samples, barSet = BarSet.Ascii)   // '#', for a terminal with no block elements
BarChart(data, barSet = BarSet.Halves)      // three levels: empty, half a cell, a whole cell
BarChart(data, barSet = BarSet.Solid)       // whole cells only, no sub-cell precision
Sparkline(samples, barSet = BarSet.uniform("*", empty = Some("·")))
```

The default, `BarSet.Eighths`, has no empty glyph, so the cells above a bar are left exactly
as they were — that is what lets a chart be drawn over an existing background. The other
built-in sets do have one, so each bar gets a visible track behind it, painted in the bar's
own style. Every glyph in a set must be a single terminal column wide, because a bar is
measured in whole columns and a two-column glyph would spill into the bar next door.

For custom plots, `canvas(xBounds, yBounds)(shapes*)` provides points, segments,
polylines, rectangles, and circles. Charts can use braille or half-block resolution
depending on density.

## Feedback and motion

Animations need one thing from you — a tick rate — and nothing else:

```scala
override def config: RunnerConfig = RunnerConfig(tickRate = Some(80.millis))

def view(using ReactiveScope, Theme): Element = spinner("deploying")
```

There is no counter to declare, advance, or thread through. The animated elements
read the ambient `AnimationClock`, and because that read is *tracked*, only a view
actually rendering an animation is repainted by the ticks — a screen with no spinner
on it is left alone. Hand-rolling `ticks.get` in the view repaints the whole app
forever instead, whether anything is moving or not.

- `spinner(label)` and `animatedText(content)` animate on the ambient clock;
- `skeleton()`, `indeterminateBar()`, and `marquee(content)` show work without known
  progress — a ticker sets its reading rate with `marquee(headline).speed(6.0)` in cells
  per second, the blanks between repetitions with `.gap(8)`, and `.period(10.seconds)`
  says the rate as a lap time instead, so two tickers of different lengths can be put in
  step;
- `progressBar(ratio)`, `progressBar(done, total)` and `gauge(ratio)` show bounded
  progress;
- toasts, splash screens, and post-render effects live in the app/runtime layer.

Every animation is a pure function of elapsed time, so each of these has a
`…At(elapsed)` twin — `spinnerAt`, `skeletonAt`, `indeterminateBarAt`, `marqueeAt`,
`animatedTextAt` — for driving from a clock of your own. `AnimationClock.freezeAt(...)`
pins the clock so a test can assert an exact frame without waiting for wall time.

Speeds are wall-clock, not tick counts. A preset that holds each frame for 80ms looks
the same in an app ticking every 50ms and one ticking every 200ms; expressing speed in
ticks would make the same spinner read as sluggish in one and frantic in the other.

Use a real percentage when you know one; use an indeterminate widget only when you
do not. Respect reduced-motion needs by offering a config option or static fallback —
`IndeterminateMotion.Pulse` and `SpinnerPreset.Ellipsis` are the quietest built-ins.

### Spinner presets

`spinner` defaults to `SpinnerPreset.Dots`; `.preset(...)` swaps the animation.

```scala
spinner("deploying").preset(SpinnerPreset.Arc)
```

Presets are grouped by the glyph repertoire they need, which is what decides whether
one is safe on a given terminal:

| Catalogue | Presets | Needs |
|---|---|---|
| `AsciiPresets` | `line`, `ellipsis`, `bouncing-bar` | nothing — safe anywhere, including a log |
| `BraillePresets` | `dots`, `dots-orbit`, `dots-pulse`, `dots-ring`, `dots-wave` | a font with the braille block |
| `BlockPresets` | `grow-vertical`, `grow-horizontal`, `quadrant`, `square`, `circle-halves`, `circle-quarters`, `arc`, `triangle`, `pipe`, `star`, `toggle`, `points`, `arrow`, `bouncing-ball`, `layers`, `balloon` | block, box-drawing and geometric-shape glyphs |
| `EmojiPresets` | `moon`, `earth`, `clock`, `hearts` | color emoji; **two columns per frame** |

A preset carries its own speed as `frameDuration`. `.slowedBy(2)`, `.reversed`,
`.atFps(8)` and `.withFrameDuration(...)` derive variants — `.slowedBy` and `.atFps`
are on the element too, so `spinner("x").atFps(4)` needs no preset value. 
`SpinnerPreset.byName("arc")` resolves one from a config file or a `--spinner` flag.

Frames are padded to the widest frame in the set, so a preset with ragged frames
(`ellipsis`, `bouncing-bar`) never shoves its label back and forth.

### Progress-bar presets

`.preset(...)` picks which glyphs a bar draws with. The nine built-ins:

| Preset | Look | Sub-cell |
|---|---|---|
| `Line` (default) | `━━━━────` | no |
| `Blocks` | `████▌` | yes — eighth blocks |
| `BlocksShaded` | `████▌░░░` | yes |
| `Shaded` | `███▓░░░` | yes |
| `Ascii` | `####----` | no |
| `Arrow` | `===>----` | no |
| `Dots` | `⣿⣿⣷⢀⢀` | yes |
| `Baseline` | `▄▄▄▁▁▁` | no |
| `Minimal` | `▪▪▪···` | no |

A preset with `partials` draws progress finer than its own cell grid — eight partial
blocks turn a 20-column bar into 160 distinguishable positions. That also changes the
rounding, deliberately: sub-cell presets **floor** the fill and render the remainder as
the boundary glyph, so the bar never claims progress that has not happened, while
whole-cell presets round to nearest because that is the closest a cell can get.

`indeterminateBar` draws from the same vocabulary and adds `.motion(...)`:

| Motion | Behaviour |
|---|---|
| `Bounce` (default) | a segment sliding to the far edge and back |
| `Sweep` | a segment leaving one edge and reappearing at the other |
| `Comet` | a bright head with a tail fading behind it |
| `Pulse` | the whole track brightening in place — no travel, the quietest option |

`.period(duration)` sets how long one full traverse takes, so several bars can be made
to move in step regardless of their widths.

### Captions

A bar's caption is a `ProgressLabel`, not a nullable string:

| Element call | Renders |
|---|---|
| `progressBar(r)` | `42%` — the default |
| `progressBar(r).label("syncing")` | `syncing` |
| `progressBar(r).labelled("syncing")` | `syncing 42%` |
| `progressBar(r).bare` | nothing; the bar takes the whole row |

`gauge` carries the same trio — `gauge(r)`, `gauge(r).label("syncing")`,
`gauge(r).labelled("syncing")`, `gauge(r).bare` — so the two progress meters are
captioned the same way whichever one a view reaches for. They also draw the same two
colours: both read `track` and `fill` from the theme's `loading` palette, so a gauge
next to a progress bar cannot come out in a different scheme.

### Theming the animations

Colors come from the ambient `Theme`'s `loading` palette, resolved where the element
is written, so re-theming an app re-themes its spinners, gauges and bars with no
call-site change:

```scala
final case class LoadingTheme(
    spinner: Style,   // the moving glyph
    label: Style,     // the caption beside it
    track: Style,     // the unfilled part of a bar
    fill: Style,      // the filled part
    band: Style,      // a skeleton's sweeping highlight
    fillRamp: Option[ColorRamp] = None,
)
```

A custom theme gets a coherent palette for free from its own semantic styles:

```scala
val mine = Theme.Dark.copy(
  name = "mine",
  loading = LoadingTheme.from(accent = myAccent, muted = myMuted, surface = mySurface),
)
```

Anything set at the call site layers on top of the theme, and the glyph and the label
style independently:

```scala
spinner("syncing").fg(Color.Magenta)              // recolors the glyph, not the label
progressBar(used, total).ramp(ColorRamp.Traffic)     // green → amber → red as it fills
```

`ColorRamp` takes any number of stops, which matters: interpolating straight from green
to red passes through a muddy brown, so the common progress ramp needs amber in the
middle. Built-ins are `Traffic` (green→amber→red), `Recovery` (its reverse), and `Heat`.

A ramp is opt-in rather than a theme default because its meaning is call-site specific:
`Traffic` reads as "filling up towards trouble", which is right for disk usage and
wrong for a download.

The showcase's **Loading** tab renders every preset at once — run
`./mill examples.showcase.run` to pick one by eye.

### Shape spinners

`spinner` says "working" inside one glyph. When there is a pane to fill rather than a
status line, three widgets say it in an area — all on the same ambient clock, all pure
functions of elapsed time.

```scala
orbitSpinner()                              // a circle with a comet arc, fitted to its area
orbitSpinner().path(OrbitPath.Square).radius(4)
linearSpinner().bouncing                    // a head travelling a one-cell track
spinnerGrid().preset(SpinnerPreset.DotsRing)
```

**`orbitSpinner`** draws a figure at sub-cell resolution with a bright arc chasing round
it. `radius` is in **dots**, not cells, because that is the resolution the shape is drawn
at — a radius in cells could not express the difference between the two smallest legible
rings. `sweep` is the fraction of the lap that is lit, not a dot count, so the arc
subtends the same angle at every radius.

| Method | Effect |
|---|---|
| `.path(OrbitPath.Circle)` or `.path(OrbitPath.Square)` | the loop the arc travels |
| `.radius(dots)` | pins the size; unset, it fills its area |
| `.sweep(fraction)` | how much of the lap is lit — `.sweep(1.0).solid` is a static "queued" ring |
| `.solid` or `.ramp(ColorRamp.Heat)` | a uniform window, or a graded comet tail |
| `.thickness(dots)` | thickens arc and path inwards; worth it above ~radius 8 |
| `.reversed`, `.period(d)` | direction and revolution time |
| `.markers("*")` or `.halfBlocks` | the ASCII and no-braille-font floors |

**One style per cell — the constraint this widget is shaped around.** A braille cell packs
eight dots but carries one `Style`, so dot resolution is 2×4 per cell while *colour*
resolution is 1 per cell. Two rules follow, both chosen rather than fallen into: masks are
OR-ed, so a cell holding both arc and path keeps every dot and the resting ring never
erodes; styles take the brightest dot, so such a cell reads as arc and the head is never
overwritten by its own tail. The budget, concretely: a radius-4 braille ring is 24 dots but
only 11 cells, so a ramped comet shows 11 shades, not 24. The alternative — one dot per
cell, so colour and geometry agree — throws away the resolution braille was chosen for.

A consequence worth knowing: because the mask never erodes, the **glyphs are static** and
the entire animation lives in the per-cell style. On a terminal rendering neither colour
nor dim, an orbit spinner is a still ring. Reach for `spinnerGrid` there instead.

**`linearSpinner`** runs a head along a one-cell track, clipping itself to a single row or
column so it cannot smear. Its three knobs are deliberately orthogonal — `LinearPath` is
the walk, `LinearTrail` is the shading, `trailSlots` is the length — so a bouncing head
with a comet tail and a wrapping window of solid slots are the same widget with different
arguments, rather than two modes in which half the options are silently ignored. A bounce
has no dwell frame at the turn; the extreme slots are visited once per cycle and the
interior twice.

**`spinnerGrid`** turns any `SpinnerPreset` into an area-filling block by giving each slot
a time offset. It *consumes* the preset catalogue rather than extending it, which is the
point of it existing: a preset is a function of time alone and can never carry a spatial
offset, yet every preset already in the catalogue becomes an area animation the moment it
is put here. `.phase(...)` chooses `Uniform` (lockstep — the reduced-motion member),
`Diagonal`, or `Radial`. It is also the one place in this family where per-slot colour is
free, because every slot holds exactly one frame — `.ramp(...)` shades the block by phase
with none of the compromise above.

The showcase's **Loading** tab renders all of them animating; the gallery scrolls.

## Animated text

`animatedText(content)` applies a time-based effect to a string, on the same ambient
clock as the spinners. `.effect(...)` chooses which:

| Effect | What it does | Notes |
|---|---|---|
| `Wave(crestWidth, cellsPerSecond)` | a bright crest travelling through the text | the default; survives on a terminal with no color, because it moves emphasis rather than hue |
| `Typewriter(charactersPerSecond, cursor)` | reveals one grapheme at a time behind a blinking cursor | the cursor stops blinking once the text is complete, so a finished line reads as finished |
| `Gradient(ramp, cellsPerSecond)` | colors each grapheme from a `ColorRamp`, scrolling the ramp | the text stays put; the ramp moves through it |
| `Shimmer(sparkle, period)` | a highlight sweeping across with a sparkle at its head | rests between sweeps, which is what stops it reading as a strobe |
| `Bounce(trail, cyclesPerSecond)` | a vertical wave through the text with a dimmed after-image | the only effect that needs more than one row; at one row it degrades to a flat line |

```scala
animatedText("Deploying…").effect(TextEffect.Typewriter())
animatedText("glyphora").effect(TextEffect.Gradient(ColorRamp.Heat)).length(1)
animatedText("LOADING").effect(TextEffect.Bounce(trail = 2)).length(4)
```

Each effect carries its own knobs rather than `AnimatedText` carrying the union of all
of them, so you cannot set a typewriter's cursor on a shimmer and have it quietly
ignored. `heightAt` reports the rows an effect needs — only `Bounce` asks for more than
one.

## Notices and badges

`NoticeLevel` — `Success`, `Info`, `Warning`, `Error` — is the single severity
vocabulary, shared by notices, badges, and `TuiApp.notify`'s toasts. Its `icon` is one
column wide for every level so a stacked column of notices stays aligned, and its `tag`
(`OK`, `INFO`, `WARN`, `FAIL`) is the short form for a badge.

```scala
notice("8 services updated", NoticeLevel.Success).at(LocalTime.now())
notice("could not reach the registry", NoticeLevel.Error).wrapped
```

Renders as `[12:04:31] ✔ 8 services updated`. The timestamp is **passed in**, never read
by the widget: a widget that called `LocalTime.now()` would render a different frame on
every repaint, so a redraw triggered by something else entirely would silently change
the displayed time. Stamp the message when the event happens, which is the moment the
reader cares about. `.wrapped` lets a long message grow instead of clipping.

`badge(label)` is a short inline label, in three variants that read at different
volumes:

| Variant | Draws | Use when |
|---|---|---|
| `Solid` (default) | reversed text, padded — ` NEW ` | the status must not be missed |
| `.outline` | bracketed, no block of color — `[NEW]` | it sits inside a line of prose |
| `.dot` | a colored dot then plain text — `● ready` | every row in a list has one and none should shout |

```scala
row(text("api"), badge(NoticeLevel.Error).outline)   // api [FAIL]
row(text("worker"), badge("3 pending").dot)
badge("BETA").fg(Color.Magenta)
```

`badge(level)` uses the level's own tag and color; `badge(label)` takes the theme's
accent and accepts any `.fg(...)`. Both report a `widthAt`, so a caller can size a
column for them rather than guessing.

## Drop to a raw Widget

The DSL is not a separate renderer. Any core `Widget` can become a leaf:

```scala
import io.worxbend.tui.core.*
import io.worxbend.tui.widgets.Paragraph

val raw = Paragraph(Text.raw("Rendered directly"), overflow = Overflow.Wrap)
val element = widget(raw).fill
```

A widget that knows how much room its content needs says so by implementing
`io.worxbend.tui.core.Measured` — `heightAt(width)` for content whose height depends on
the width it wraps at, `widthAt(height)` for content sized the other way. Returning
`None` means "I cannot say", and callers must treat that as unmeasurable rather than as
a size. `widget(...)` needs no measurement wiring of its own: a wrapped `Paragraph`,
`Markdown`, `Notice`, `Badge`, `Spinner`, `BigText`, `AnimatedText` or `Tooltip` already
answers, so a `scrollView` over one scrolls the full content. `Paragraph` answers
`widthAt` too: the longest of its lines, measured in terminal columns, which is the
narrowest area in which nothing is clipped and nothing has to wrap.

Measuring is not the same as claiming, though, and only the measurement pass consults
`Measured`. In a `row` or a `column` a wrapped widget claims everything its siblings
leave over, because the layout solver has not picked a width yet and so cannot ask
`heightAt`. Say the height with `.rows(n)`:

```scala
column(
  text("above"),
  widget(myOneRowWidget).rows(1), // without this it takes every leftover row
  text("below"),
)
```

Prefer `.rows(n)` over `.length(n)` here. `.rows(n)` says "n rows tall, whatever width
the container has", which is a fact about the widget and holds wherever you put it.
`.length(n)` sets a single constraint that the container applies along **whichever axis
it runs** — so `widget(divider).length(1)` is one row tall inside a `column` and one
*column wide* inside a `row`. Reach for `.length(n)` only when the number really is the
container's decision.

Or use widgets without the DSL at all:

```scala
val buffer = Buffer(Rect(0, 0, 40, 5))
raw.render(buffer.area, buffer)
```

That escape hatch is useful for embedding, custom render loops, and widget library
tests. See [Architecture](./architecture) and [Testing](./testing).

## Catalog by tier

The internal tiers document dependency order, not quality:

1. **Foundation** — block, row/column, spacer, rule, paragraph, list view, table,
   tabs, gauge, line gauge, sparkline, scrollbar, button, checkbox, toggle, select,
   radio group, slider, text input, tree.
2. **Visualization** — canvas, chart, bar chart, column chart, stacked bar chart,
   pie chart, heatmap, dual sparkline, calendar.
3. **Rich content** — spinner and its presets, spinner grid, orbit spinner, linear
   spinner, animated text, marquee, skeleton, indeterminate bar, big text, notice,
   badge, tooltip, dialog, markdown, syntax highlighter.
4. **Application-scale state** — data table, directory tree, text area, log, menu,
   paginator, scroll view, image, and links.

Every built-in has render-to-`Buffer` tests. Interactive DSL wrappers additionally
have focus and event-routing tests.
