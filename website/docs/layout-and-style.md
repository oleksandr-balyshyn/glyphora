---
title: Layout & style
description: Compose rows, columns, constraints, alignment, borders, and reusable visual styles in glyphora.
---

# Layout & style

Terminal layout is geometry, not pixels. glyphora divides the available cell grid
with constraints, then renders each child inside the rectangle it receives. Once
that rule clicks, layouts stay predictable through resizes and nested panels.

> **Core idea:** a `row` divides width, a `column` divides height, and a constraint
> on each child says how much of that axis it claims.

## Start with rows and columns

```scala
column(
  topBar("deployctl").length(1),
  row(
    panel("Services")(serviceList).length(28),
    panel("Details")(details).fill,
  ).fill,
  statusBar(bindings).length(1),
)
```

Read it from the outside in:

- the outer `column` reserves one row for the top bar and one for the status bar;
- its middle row uses `.fill`, so it receives all remaining height;
- inside that row, the service panel receives 28 columns and details fills the rest.

`panel`, `text`, inputs, and other elements have sensible preferred sizes. Add an
explicit constraint only when the surrounding composition needs one.

## Choose a constraint

| Extension | Meaning | Typical use |
|---|---|---|
| `.length(12)` | exactly 12 cells | toolbars, sidebars, single-line regions |
| `.percent(40)` | 40% of available axis | balanced master/detail layouts |
| `.ratio(1, 3)` | exactly a third of the axis | splits a whole percentage cannot say |
| `.fill` | share all space left after fixed constraints | main content |
| `.fill(2)` | take twice the remaining share of `.fill(1)` | weighted columns |
| `.minSize(8)` | at least 8 cells when solving | important compact content |
| `.maxSize(30)` | no more than 30 cells | readable text or narrow controls |

A percentage takes its share and leaves the rest as free space, which `flex` then
positions — `row(a.percent(20), b.percent(60))` is 20/60 with a fifth of the axis
unused, not 30/70. Percentages that *do* claim the whole axis absorb the rounding
remainder between them, so `.percent(33)` three times fills the container exactly at
every width rather than leaving a stray column at the right edge.

Weighted fills make proportions clear without hardcoding terminal width:

```scala
row(
  panel("Queue")(queue).fill(1),
  panel("Timeline")(timeline).fill(2),
  panel("Health")(health).fill(1),
).gap(1)
```

## Position content deliberately

`centered(width, height)` is convenient for dialogs and focused empty states:

```scala
centered(42, 9) {
  panel("No deployments")(
    text("Connect a cluster to begin.").bold,
    text("Press c to configure one.").dim,
  ).rounded
}
```

### Align one row of text on its own

`Alignment` also travels *inside* a block of text: a `line(...)` can say where that single
row sits, and it wins over whatever the widget drawing it was told to do. That is what a
label-on-the-left, total-on-the-right row wants, with no hand-counted padding between them:

```scala
column(
  line(span("Deployments")).leftAligned,
  line(span("42 healthy"), span("  ")).rightAligned,
  line(span("updated 12s ago")).centered,
)
```

The builders are `leftAligned`, `centered`, `rightAligned`, and `aligned(Alignment.Right)`
when the choice is computed rather than written out. A line that sets none of them keeps
the old behaviour: it is placed the way the surrounding widget was told to place it. The
offsets are measured in terminal columns via `CharWidth`, so a CJK or emoji line lands
where it looks like it should rather than where its character count would put it.

For independent horizontal and vertical alignment, use `place`:

```scala
place(
  width = 36,
  height = 5,
  horizontal = Alignment.Right,     // right edge of the area
  vertical = VerticalAlignment.Top, // top of the area
)(toastPreview)
```

Each axis is named in its own vocabulary. `horizontal` takes `Alignment` — the same
`Left`/`Center`/`Right` enum that positions a `Block` title and a `Paragraph`'s text — and
`vertical` takes `VerticalAlignment`, whose cases are `Top`, `Middle`, and `Bottom`.
`Flex` — `Start`, `End`, `Center`, `SpaceBetween`, … — is a different question and stays:
it says how leftover space is shared out among *many* children, not where *one* block
sits.

App-oriented presets cover frequent shapes:

```scala
sidebarLayout(navigation, content, paneWidth = 26)
masterDetail(projectList, projectDetails, masterWidth = 32)
```

## Overlay at an exact offset

`centered` and `place` both *align* content inside the area they are handed. When you
already know the exact cell an overlay belongs at — beside the row the pointer is over,
under the control that has focus — use `positioned` instead and paint it over the base
with `layers`:

```scala
val hoveredRow = Signal(0) // whichever row your app already tracks

def view(using ReactiveScope, Theme): Element =
  layers(
    panel("Deployments")(deploymentTable),
    positioned(dx = 34, dy = hoveredRow.get + 2, width = 30, height = 3)(
      tooltip("Rolled back 4 minutes ago by ana."),
    ),
  )
```

Three things are worth knowing before you reach for it:

- `dx` and `dy` are offsets from the **top-left corner of the area this element is
  handed**, not absolute screen coordinates. A `positioned` nested inside a panel is
  placed relative to that panel, so it keeps working when the panel moves.
- `width` and `height` size the box the content renders into, and the box is **clipped**
  to the surrounding area. An offset that runs off the right edge draws the part that
  fits and nothing more: it never spills onto a neighbouring pane and it never throws.
- It only paints. It claims no space from the row or column around it, which is exactly
  why it belongs inside `layers` over a base element rather than beside one.

This is the mechanism the toolkit's own overlays are built from — a toast is a `notice`
inside a `positioned` box, which is how the stack can sit against the right edge with
every row a different width.

## Pad inside a border

A `panel` reserves blank cells between its border and its children:

```scala
panel("Summary")(summaryLines).padding(1)                        // the usual case
panel("Summary")(summaryLines).padded(Padding.horizontal(2))     // columns only
panel("Summary")(summaryLines).padded(Padding.left(2))           // one side only
panel("Summary")(summaryLines)
  .padded(Padding(left = 4, right = 1, top = 0, bottom = 0))     // each side named
```

`.padding(n)` is deliberately *not* `n` cells on all four sides. A terminal cell is about
twice as tall as it is wide, so one blank row eats about twice the screen one blank
column does, and a 24-row terminal cannot spare a row top and bottom as cheaply as an
80-column one can spare a column. `.padding(n)` therefore reserves `n` rows above and
below and `2 * n` columns either side — `Padding.proportional(n)` — which is what reads
as an even margin. `Padding.zero`, `.uniform`, `.horizontal`, `.vertical` and
`.symmetric(x, y)` name the other shapes, and `.left`, `.right`, `.top` and `.bottom`
pad a single side — worth using, because the case class takes `(left, right, top,
bottom)` rather than the CSS order, so a positional `Padding(2, 0, 0, 0)` is easy to
write for the wrong side.

Padding is charged to the panel's measured height too, so a padded panel inside a
`scrollView` still reports the rows it really occupies.

## Distribute leftover space

Rows and columns support flex-like packing when their children do not consume all
available space:

```scala
row(
  button("Cancel", cancel),
  button("Deploy", deploy),
).gap(2).flexEnd
```

Available modes are `.center`, `.spaceBetween`, `.spaceAround`, `.spaceEvenly`, and
`.flexEnd`, plus `.flex(mode)` and `.gap(cells)`. They matter only when space
remains; a `.fill` child intentionally consumes that space first.

These helpers exist only on `row`, `column`, and `panel` — the containers that lay
children out along an axis. (A `panel` stacks its children with the same widget a
`column` does, so `panel("Logs")(a, b).gap(1)` spaces them inside the border without a
`column` in between.) Writing `text("x").center` is a compile error rather than a call
that quietly does nothing. `.rounded` and `.doubleBorder` are typed the same way: they
exist only on `panel`.

## Style elements fluently

Styling calls return a new element, so they chain naturally and never mutate a
shared widget:

```scala
text("production")
  .bold
  .fg(Color.White)
  .bg(Color.Red)

panel("Audit log")(logView).rounded
panel("Danger zone")(dangerView).doubleBorder.fg(Color.Red)
```

The built-in modifiers are `.bold`, `.dim`, `.italic`, `.underline`, `.reverse`,
`.fg(...)`, and `.bg(...)`. Use `.styled` when you need a complete
`Style` transformation.

`.fg`/`.bg` name the same two things `Style.withFg`/`withBg` do, and the same two things
every other terminal toolkit calls them. On a `Style` the builders keep the `with`
prefix, because `Style` is a case class whose fields are already called `fg` and `bg` and
a field and a method cannot share a name.

A `Style` also remembers what it was asked to turn *off*. `Style.Default.notBold` is
not the same value as `Style.Default`: it carries "bold is cleared", so layering it
over a bold base removes the bold instead of being ignored. That is what makes one
element opt out of an inherited style:

```scala
withStyle(_.bold) {
  column(
    text("headline"),                  // bold, inherited
    text("footnote").styled(_.notBold), // opts out
  )
}
```

The `not*` builders and the plain ones are last-call-wins in both directions:
`.notBold.bold` is bold, `.bold.notBold` is not.

`Modifiers.All` is the whole set of text attributes in one value, so
`style.without(Modifiers.All)` clears every attribute without naming the eight of them.
The bitset operators are `a | b` (the flags set in either), `a.without(b)` (the flags in
`a` that are not in `b`), and `a & b` (the flags set in both — for asking what two styles
agree on).

To drop *everything* an inherited style said, patch `Style.Reset` on top of it, or call
`.reset` on the style, which is the same thing:

```scala
val themed = Style.Default.withFg(Color.Cyan).bold
themed.patch(Style.Reset) // default-colored, unbold text
themed.patch(Style.Default) // unchanged: Default is silent about every field
```

`Style.Reset` sets the foreground, background and underline color to `Color.Reset` (the
terminal default) and records every text attribute as cleared, so all of that survives
further layering. The two fields it leaves alone are `underlineStyle` and `link`, because
`patch` reads `UnderlineStyle.None` and an absent link as "silent" rather than "off" —
call `withUnderlineStyle(UnderlineStyle.None)` yourself to remove a curly underline.

## Style factories

For the common case of a style that sets one or two things, the `Style` companion has
short factories. Each is exactly the builder chain beside it:

| Factory | The same value as |
| --- | --- |
| `Style.fg(Color.Green)` | `Style.Default.withFg(Color.Green)` |
| `Style.bg(Color.Black)` | `Style.Default.withBg(Color.Black)` |
| `Style.of(Color.Green, Color.Black)` | `Style.Default.withFg(Color.Green).withBg(Color.Black)` |
| `Style.mods(Modifiers.Bold)` | `Style.Default.bold` |

They are plain methods rather than implicit conversions from `Color`, so a call site that
passes a style always says the word `Style`.

Apply a base style to a whole subtree with `withStyle`:

```scala
withStyle(_.withFg(Color.Cyan)) {
  column(
    text("connected").bold,
    text("latency 12 ms"),
  )
}
```

Descendants can still add or override their own style. Raw `widget(...)` leaves and
images intentionally ignore the element style because their renderer owns its
cells directly.

## Work with text values directly

Underneath the elements are three plain immutable values from `tui-core`, and one
`import io.worxbend.tui.dsl.*` reaches all of them. A `Span` is a run of characters plus
one `Style`; a `Line` is a sequence of spans making up one terminal row; a `Text` is a
sequence of lines.

### Restyle a value you were handed

All three answer `styled(transform)`, which runs every span's `Style` through the
function and leaves the characters alone, so a value returned by a helper can be
adjusted instead of taken apart and rebuilt:

```scala
val label = "OK".styled(_.withFg(Color.Green)) // a Span
label.styled(_.bold)                           // green and bold
Text.raw(body).styled(_.italic)                // every line italic
```

`under(base)` layers the other way round: `base` goes *underneath* what each span already
chose, so a span that set a colour keeps it and `base` fills in only what was left unset.
That is the direction a theme colour travels:

```scala
Text.raw(body).under(theme.muted)
```

`patchStyle(style)` is `under` with the two layers swapped: the argument goes on top and
wins wherever it says something, while everything it stays silent about survives. "Make
this already-styled line italic without disturbing its colours" is one call:

```scala
line.patchStyle(Style.Default.italic)
```

A `Line` and a `Text` hold no style of their own — a line is its spans — so all three
methods are a fold over the spans rather than a field being set. That is why they compose
the way plain `Style` calls do.

### Show a secret without showing it

`maskedInput` hides what a user is *typing*. For a secret that is merely displayed — in a
paragraph, a table cell, a list row, a log line — wrap it in `Masked`:

```scala
val token = Masked(apiToken)        // hidden behind •
val pin   = Masked(accountPin, "*") // or behind a mask of your choosing

text(token.value)
line("token: ", pin.toSpan)
```

`Masked` emits one mask glyph per grapheme cluster, so an emoji or an accented letter is
hidden by exactly one character. Writing `"*" * secret.length` by hand does not: that
counts UTF-16 code units, so it produces more mask characters than there are characters to
hide, which both leaks the shape of the secret and misaligns the row. Its `toString` is
the mask too, so a `Masked` that reaches a log line or an assertion message does not print
the secret.

This hides characters on a screen. It is not encryption: the original text is still in
memory and still readable through `content`.

### Walk text cell by cell

A *grapheme cluster* is what a reader thinks of as one character: a base character plus
whatever attaches to it, so `e` followed by a combining acute accent is one cluster, and
so is a multi-code-point emoji. It is also the smallest thing a terminal cell can hold.

`styledGraphemes(base)` on a `Span` or a `Line` steps through the text one cluster at a
time, each one already carrying the style it will be drawn in — the span's own style
layered over the `base` you pass, so nothing downstream has to resolve it again:

```scala
val cells = line.styledGraphemes(theme.body).toList
cells.map(_.width).sum == line.width // always
```

Reach for it when you need per-cell information: working out which character a mouse click
landed on, or writing a reflow rule the built-in wrapping does not cover. The returned
iterator is stateful, single-use and owned by the calling thread — walk it once, and call
the method again rather than storing one.

### Build a value up a piece at a time

These are immutable values, so nothing is pushed into them: each helper returns a copy and
leaves the receiver alone. `Line.appended(span)` adds a span to the right of a row,
`Text.appended(line)` adds a row below a block, and `appendedAll` does the same with a
whole value. `Line.Empty` and `Text.Empty` are the identities to fold from:

```scala
val row = spans.foldLeft(Line.Empty)(_.appended(_))
val doc = rows.foldLeft(Text.Empty)(_.appended(_))
```

`+` and `++` are the same two helpers as operators, for the short inline cases:
`Span.raw("ok ") + count` makes a one-row `Line` out of two spans, `line + span` and
`line ++ otherLine` extend a row to the right, and `text + line` and `text ++ otherText`
add rows below. There is no operator for stacking two `Line`s vertically — that would give
`+` two different axes depending on the type of its right-hand side — so write
`Text(Seq(first, second))` when you mean "one above the other".

`Text.appendedToLast(span)` extends the *last* row rather than starting a new one, and it
owns the case that is easy to get wrong: a text with no rows at all has no last row, so
one is started holding just that span. A text whose last row exists but is empty is not
that case — the span joins that row and the row count does not change.

### Read the characters back out

`plainText` flattens a value back to an ordinary string with every style dropped — the
text a user would copy out of the terminal. Use it for logging, clipboard payloads and
test assertions:

```scala
val line = Line(Seq(Span("ok", Style.Default.withFg(Color.Green)), Span(" done", Style.Default)))
line.plainText            // "ok done"
Text.raw("a\nb").plainText // "a\nb"
```

`Text.plainText` joins the lines with `\n` and adds no trailing newline, so
`Text.raw(s).plainText == s` for any `s` without a carriage return.

Do not measure with it. `plainText.length` counts UTF-16 code units, which is not the
number of terminal columns the text occupies: one CJK character is one code unit and two
columns, and a combining accent is one code unit and no column at all. `width` is the
accessor that answers in columns, and it goes through `CharWidth`.
## Derive one color from another

`Color` can compute related colors instead of making you type a second hex literal.
`Color.lighten(c, amount)` and `Color.darken(c, amount)` fade toward white and black,
`Color.mix(a, b, t)` blends two colors, and `Color.gradient(from, to, steps)` returns an
evenly spaced ramp. Each one also reads as a method on the color itself —
`accent.mixedWith(theme.surface, 0.3).darken(0.1)`.

Fading toward white also drains the color out of a hue: a red lightened by 60% is
noticeably pinker and greyer than the red it came from. When you want the *same* color
at a different brightness, work in HSL — Hue (the angle on the color wheel, in degrees),
Saturation and Lightness (both fractions from `0.0` to `1.0`):

```scala
val brand = Color.hex("#c83232").getOrElse(Color.Red)

brand.withLightness(0.8)   // same hue, a pale tint of the brand color
brand.withSaturation(0.2)  // same hue and brightness, nearly grey
brand.rotateHue(180)       // the complementary color
brand.asHsl                // (hue, saturation, lightness), for your own arithmetic
```

Evenly spaced hues at one saturation and lightness make a categorical palette — colors
that are easy to tell apart but visually belong to one set, which is what a multi-series
chart wants:

```scala
val seriesColors = Seq.tabulate(6)(i => Color.hsl(i * 60.0, 0.65, 0.55))
```

`Color.hsl` wraps the hue (`-30` and `330` are the same red) and clamps saturation and
lightness, so generated palettes cannot produce an out-of-range color. `Color.toHsl(c)`
is the inverse, and it answers for named and 256-color values too by going through their
RGB approximation.

## Build with themes, not scattered colors

For application chrome and reusable components, take the `Theme` as a context
parameter and draw the color from a semantic role:

```scala
def deploymentStatus(name: String, healthy: Boolean)(using theme: Theme): Element =
  val tone = if healthy then theme.success else theme.error
  text(s"● $name").styled(_ => tone)
```

That `(using theme: Theme)` is not ceremony: `view` receives the app's own theme (its
signature is `def view(using ReactiveScope, Theme)`), and the compiler passes it down
to helpers that ask for it. A helper that does *not* ask falls back to the library
default and silently renders dark-theme colors in a light-theme app.

`Theme.Dark`, `Theme.Light`, and `Theme.HighContrast` are built in. A custom theme is a
value of semantic styles — `primary`, `accent`, `muted`, `error`, `warning`, `success`,
`surface`, `border`, `focus` — plus the three grouped sub-palettes `loading`,
`markdown`, and `syntax`. Several factories read them for you already: `panel` and
`rule` frame themselves with `border`, `dialog` uses `primary` and `focus`, the whole
progress-and-spinner family reads `loading`, and `markdown` renders through
`markdown`. Every element that highlights a selected row — `list`, `tree`, `menu`,
`selectionList`, `filePicker`, `directoryTree` — draws it in `focus`, focused or not,
because the focus pass stamps the theme's cue onto the whole tree rather than onto the
one element holding the keyboard. See [The app shell](./app-shell) for live switching.

## Colors from text, and back again

A color does not have to be written in Scala. `Color.parse` reads the three
spellings a configuration or theme file is likely to contain, and returns
`Either[String, Color]` — a `Left` carries a message naming what it could not
read, so a bad line is something your program reports rather than an exception
it has to catch:

```scala
Color.parse("bright cyan")  // Right(Color.BrightCyan)
Color.parse("#1e88e5")      // Right(Color.Rgb(30, 136, 229))
Color.parse("214")          // Right(Color.Indexed(214))
Color.parse("puce")         // Left("'puce' is not a color name, a hex color like #rrggbb, ...")
```

Names ignore case and the separators a hand-written name may carry, so
`BrightRed`, `bright red`, `bright-red`, `light_red` and `brightred` are all the
same color. `light` is accepted anywhere `bright` is; `grey`, `gray` and
`dark grey` name `BrightBlack`, and `silver` names `White`.

A run of one to three digits is always read as an xterm palette index, never as
a hex color: `120` is `Indexed(120)`. Write the `#` when you mean the color
`#120`.

The `.render` method is the inverse — it writes a color in the spelling `parse`
reads back, which is what you want when saving a theme rather than the
compiler-generated `toString`:

```scala
Color.BrightBlue.render      // "BrightBlue"
Color.rgb(255, 136, 0).render // "#ff8800"
Color.Indexed(214).render     // "214"
```

Every string `.render` writes parses back to an equal color. The other direction
is lossy, because many spellings name one color.

For a table of palette constants written as hex literals, `Color.fromInt` is the
total counterpart of `Color.hex`: it takes a packed `0x00RRGGBB` integer and
always succeeds, so no `Option` has to be unwrapped to initialise a `val`.
`Color.toInt` (or the `.packed` method) goes the other way.

```scala
val Surface = Color.fromInt(0x1e293b)
val Accent  = Color.fromInt(0xf8fafc)
```

## Avoid common layout surprises

- A constraint applies along the **parent container's direction**: `.length(10)` is
  width in a row and height in a column.
- Borders consume cells. A 3-row panel has one inner row after its top and bottom
  border.
- Use `CharWidth`, not `String.length`, when custom code measures visible text.
- Give interactive elements a stable `.key("settings-name")` when conditional
  rendering might otherwise move focus to a different positional index.
- Deep fixed sizes fail on small terminals. Reserve fixed cells for chrome, then let
  primary content fill.
- Constraints reflow, but they cannot decide that a layout has stopped working. When
  a narrow terminal needs *different components* rather than smaller ones, branch on
  size — see [Responsive layouts](./responsive).

Next, browse the [Widget catalog](./widgets) or assemble these pieces into
[The app shell](./app-shell).
