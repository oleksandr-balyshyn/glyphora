---
title: The app shell
---

# The app shell

`TuiApp` is the entry point applications extend. Beyond `view`, it provides app
services and wires up the chrome presets automatically.

```scala
object MyApp extends TuiApp:
  override def theme: Theme = Theme.Dark
  override def bindings = KeyBindings(
    binding("ctrl+s", "save")(save()),
    binding("?", "help")(pushScreen(Screen(helpOverlay(bindings)))),
    binding("esc", "quit")(quit()),
  )
  def view(using ReactiveScope): Element = ...
```

## scaffold

`scaffold` composes an optional top bar, an optional sidebar (left or right of the
content), the content filling the middle, and an optional status bar:

```scala
scaffold(
  topBar = Some(topBar("myapp", tabs = Seq("Files", "Logs"), selectedTab = tab.get)),
  sidebar = Some(sidebar(directoryTree(treeState), width = 28)),
  statusBar = Some(statusBar(bindings)),
)(content)
```

- **`topBar(title, tabs, selectedTab, right)`** — title left, optional tab strip
  centered, optional right-aligned text.
- **`sidebar(content, width, onRight)`** — a side pane; `onRight = true` puts it after
  the main content instead of before.
- **`statusBar(hints)`** / **`statusBar(bindings)`** — a row of `key description`
  hints; the `KeyBindings` overload derives the hints straight from your declared
  bindings, so one declaration drives dispatch, the status-bar text, and the help
  overlay.

Layout presets for composing content directly (without the full shell):
`sidebarLayout(side, main, sideWidth)`, `masterDetail(master, detail, masterWidth)`,
`centered(width, height)(content)`.

## Themes

```scala
final case class Theme(
  name: String,
  primary: Style, accent: Style, muted: Style,
  error: Style, warning: Style, success: Style,
  surface: Style, border: Style, focus: Style,
)
```

Three built-ins — `Theme.Dark` (default, ambient `given`), `Theme.Light`,
`Theme.HighContrast`. Chrome presets and your own views draw colors from `Theme`
rather than hardcoding `Color` values, so switching themes at runtime is just
re-rendering under a different `given Theme` — read it from a `Signal[Theme]` and the
whole tree re-themes on the next render.

## Keys once, everywhere

```scala
override def bindings = KeyBindings(
  binding("ctrl+s", "save")(save()),
  binding("?", "help")(pushScreen(Screen(helpOverlay(bindings)))),
  binding("esc", "quit")(quit()),
)
```

One declaration drives dispatch, the status-bar hints, the `?` `helpOverlay`, and the
built-in `Ctrl+P` command palette (fuzzy-filtered over the same bindings, Enter runs
the selected command).

## Screens, toasts, splash

- **`pushScreen(screen)` / `popScreen()`** — a stack of `Screen`s. A *modal* screen
  (`Screen(view)`, the default) renders layered over what's beneath it with
  everything below removed from tab order; a *full* screen (`Screen.full(view)`)
  replaces the view entirely.
- **`notify(message, level, ttlTicks)`** — a tick-aged toast (`ToastLevel.Info` /
  `Success` / `Warning` / `Error`), auto-dismissed after `ttlTicks` ticks.
- **`splash: Option[SplashScreen]`** — an intro shown before the first render:
  `SplashScreen(content, effect, minimumDuration)` plays a [motion effect](./motion)
  (typically over a `bigText` logo) and holds for at least `minimumDuration`; any key
  skips it.
- **`openPalette()`** — opens the fuzzy `Ctrl+P` command palette programmatically.

All four services are `protected final` on `TuiApp`, callable from any handler
(`bindings`, `onKeyEvent`, `onMouseEvent`, `onTick`).
