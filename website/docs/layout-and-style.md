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
  horizontal = Align.End,
  vertical = Align.Start,
)(toastPreview)
```

App-oriented presets cover frequent shapes:

```scala
sidebarLayout(navigation, content, sideWidth = 26)
masterDetail(projectList, projectDetails, masterWidth = 32)
```

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
`.flexEnd`. They matter only when space remains; a `.fill` child intentionally
consumes that space first.

## Style elements fluently

Styling calls return a new element, so they chain naturally and never mutate a
shared widget:

```scala
text("production")
  .bold
  .color(Color.White)
  .background(Color.Red)

panel("Audit log")(logView).rounded
panel("Danger zone")(dangerView).doubleBorder.color(Color.Red)
```

The built-in modifiers are `.bold`, `.dim`, `.italic`, `.underline`, `.reverse`,
`.color(...)`, and `.background(...)`. Use `.styled` when you need a complete
`Style` transformation.

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

Next, browse the [Widget catalog](./widgets) or assemble these pieces into
[The app shell](./app-shell).
