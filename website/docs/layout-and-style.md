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

For independent horizontal and vertical alignment, use `place`:

```scala
place(
  width = 36,
  height = 5,
  horizontal = Alignment.Right, // far edge across
  vertical = Alignment.Left,    // near edge down: the top
)(toastPreview)
```

Both axes use `Alignment`, the same `Left`/`Center`/`Right` enum that positions a `Block`
title and a `Paragraph`'s text. On the vertical axis read `Left` as *top* and `Right` as
*bottom*: it is the near edge of the axis and the far one. (There used to be a second
enum, `Align(Start, Center, End)`, meaning exactly this and landing one keystroke from
`Alignment` in every application's scope. It is gone.) `Flex` — `Start`, `End`, `Center`,
`SpaceBetween`, … — is a different question and stays: it says how leftover space is
shared out among *many* children, not where *one* block sits.

App-oriented presets cover frequent shapes:

```scala
sidebarLayout(navigation, content, sideWidth = 26)
masterDetail(projectList, projectDetails, masterWidth = 32)
```

## Pad inside a border

A `panel` reserves blank cells between its border and its children:

```scala
panel("Summary")(summaryLines).padding(1)                        // the usual case
panel("Summary")(summaryLines).padded(Padding.horizontal(2))     // columns only
panel("Summary")(summaryLines)
  .padded(Padding(left = 4, right = 1, top = 0, bottom = 0))     // each side named
```

`.padding(n)` is deliberately *not* `n` cells on all four sides. A terminal cell is about
twice as tall as it is wide, so one blank row eats about twice the screen one blank
column does, and a 24-row terminal cannot spare a row top and bottom as cheaply as an
80-column one can spare a column. `.padding(n)` therefore reserves `n` rows above and
below and `2 * n` columns either side — `Padding.proportional(n)` — which is what reads
as an even margin. `Padding.zero`, `.uniform`, `.horizontal`, `.vertical` and
`.symmetric(x, y)` name the other shapes.

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

These helpers exist only on `row` and `column` — the two containers that lay children
out along an axis. Writing `text("x").center` is a compile error rather than a call
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

## Build with themes, not scattered colors

For application chrome and reusable components, draw from the ambient `Theme`:

```scala
def deploymentStatus(name: String, healthy: Boolean)(using theme: Theme): Element =
  val tone = if healthy then theme.success else theme.error
  text(s"● $name").styled(_ => tone)
```

`Theme.Dark`, `Theme.Light`, and `Theme.HighContrast` are built in. A custom theme is
just a value containing semantic styles (`primary`, `accent`, `muted`, `error`,
`warning`, `success`, `surface`, `border`, and `focus`). See [The app shell](./app-shell)
for live switching.

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
