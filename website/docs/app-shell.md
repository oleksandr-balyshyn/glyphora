---
title: The app shell
description: Structure a glyphora application with scaffold, themes, commands, screens, toasts, splash, and terminal services.
---

# The app shell

`TuiApp` is the batteries-included application boundary. You provide a view and
optional lifecycle hooks; it connects state tracking, focus, input routing, screen
navigation, notifications, effects, and a terminal backend.

For a small app, `view` may be the whole story. For a full-screen tool, start with
the shell below and fill in each region.

## A complete shell

```scala
import io.worxbend.tui.dsl.*
import io.worxbend.tui.widgets.{ListState, TextInputState}

final class DeployApp extends TuiApp:
  private val section = Signal(0)
  private val nav = ListState()
  private val search = TextInputState()

  override def bindings = KeyBindings(
    binding("ctrl+r", "refresh deployments")(refresh()),
    binding("ctrl+n", "new deployment")(openCreateScreen()),
    binding("?", "show keyboard help") {
      pushScreen(Screen(centered(48, 12)(helpOverlay(bindings))))
    },
    binding("q", "quit")(quit()),
  )

  def view(using ReactiveScope): Element =
    scaffold(
      topBar = Some(topBar(
        "deployctl",
        tabs = Seq("Deployments", "Events"),
        selectedTab = section.get,
        right = "production",
      )),
      sidebar = Some(sidebar(navigation, width = 25)),
      statusBar = Some(statusBar(bindings)),
    )(workspace)
```

`scaffold` builds four predictable regions: optional top bar, optional sidebar,
filling content, and optional status bar. Nothing bypasses the normal element/widget
pipeline, so each part can be styled, tested, or replaced.

## Compose the chrome

```scala
scaffold(
  topBar = Some(topBar(
    title = "glyphora",
    tabs = Seq("Widgets", "Log", "About"),
    selectedTab = selectedTab.get,
    right = "connected",
  )),
  sidebar = Some(sidebar(sidebarView, width = 24, onRight = false)),
  statusBar = Some(statusBar(bindings)),
)(mainView)
```

- `topBar` renders a title, optional tabs, and optional right-aligned status.
- `sidebar` describes content, width, and side; `scaffold` handles placement.
- `statusBar(bindings)` derives readable key hints from the same command registry
  that handles input.

For layouts without the full chrome, use `sidebarLayout`, `masterDetail`, `centered`,
or `place`; see [Layout & style](./layout-and-style).

## Declare commands once

```scala
override def bindings = KeyBindings(
  binding("ctrl+s", "save current file")(save()),
  binding("ctrl+o", "open project")(openProject()),
  binding("ctrl+t", "switch theme")(nextTheme()),
  binding("esc", "quit")(quit()),
)
```

One `KeyBinding` supplies:

- event dispatch after the focused element declines a key;
- `(key, description)` hints for `statusBar`;
- rows for `helpOverlay`;
- searchable commands in the built-in `Ctrl+P` palette.

Descriptions should be short verbs: “open project” is easier to scan than “project
opening functionality.” `KeyBindings.parseKey` accepts printable keys, named keys,
and modifiers such as `ctrl+s`, `alt+enter`, and `shift+tab`.

Focused/local handlers run before global bindings. Use local `.onKey(...)` for
behavior owned by one element, and app bindings for commands meaningful everywhere.

## Navigate with screens

A `Screen` is a tracked view pushed on the app's stack:

```scala
private def openCreateScreen(): Unit =
  pushScreen(Screen {
    centered(48, 11) {
      panel("New deployment")(
        createForm,
        text("Ctrl+S create · Esc cancel").dim,
      ).rounded.onKey(Key.Escape) {
        popScreen()
      }
    }
  })
```

`Screen(...)` is modal: it paints over the current view and removes the layers below
from tab order. `Screen.full(...)` replaces the current view entirely:

```scala
pushScreen(Screen.full(settingsPage))
```

Call `popScreen()` from the active screen to return. Focus stays inside a modal by
construction, so you do not need a separate focus trap.

## Notify without interrupting flow

```scala
notify("Deployment queued", ToastLevel.Success)
notify("Authentication expired", ToastLevel.Error, ttlTicks = 60)
dismissToasts()
```

Toasts stack in the top-right corner and age on application ticks. Set a tick rate
when you use them:

```scala
import io.worxbend.tui.runtime.RunnerConfig
import scala.concurrent.duration.*

override def config = RunnerConfig(tickRate = Some(100.millis))
```

Use toasts for confirmation and recoverable status. Keep required decisions in a
screen or dialog where they cannot disappear.

## Theme semantically

```scala
final case class Theme(
  name: String,
  primary: Style,
  accent: Style,
  muted: Style,
  error: Style,
  warning: Style,
  success: Style,
  surface: Style,
  border: Style,
  focus: Style,
)
```

`Theme.Dark`, `Theme.Light`, and `Theme.HighContrast` ship with glyphora. Built-in
chrome, palette, focus, and toasts use semantic theme roles rather than hardcoded
colors.

For live switching, keep a theme index in a `Signal`, return the selected value from
`theme`, and read the index in `view` so the tree is invalidated. A complete snippet
lives in [State & signals](./state-and-signals#runtime-theme-switching).

## Add a splash or frame effect

```scala
import scala.concurrent.duration.*

override def splash = Some(
  SplashScreen(
    content = centered(38, 5)(bigText("GLYPHORA").color(Color.Cyan)),
    effect = Effect.coalesce(700.millis),
    minimumDuration = 1.second,
  )
)
```

The splash appears before the first normal view and any key skips it. `runEffect`
applies a post-render effect to the whole current frame:

```scala
runEffect(Effect.parallel(
  Effect.fadeIn(250.millis),
  Effect.sweepIn(350.millis),
))
```

Both need ticks to animate; a splash supplies a 50 ms tick automatically when the
app has none. See [Motion](./motion).

## Use terminal services safely

`TuiApp` exposes protected services for operations that need the active backend:

```scala
copyToClipboard(currentUrl)

suspend {
  val editor = sys.env.getOrElse("EDITOR", "vi")
  new ProcessBuilder(editor, selectedPath.toString)
    .inheritIO()
    .start()
    .waitFor()
}

printAbove("deployment finished", s"id: ${deployment.id}")
```

- `copyToClipboard` uses OSC 52 where supported;
- `suspend` restores cooked mode and the normal screen while an external program
  runs, then re-enters the TUI and forces a full repaint;
- `printAbove` writes durable lines into scrollback above the live interface.

These are no-ops or unavailable before a runner is active, so invoke them from event
handlers rather than constructors.

## Lifecycle hooks

| Hook | When to use it |
|---|---|
| `config` | tick cadence and backend behavior |
| `theme` | active semantic theme |
| `bindings` | app-wide commands |
| `splash` | optional launch composition |
| `onTick()` | advance frame-oriented state; never block |
| `onTerminalFocus(focused)` | pause/resume activity when mode-1004 focus events arrive |

The full shell in action is
[`examples/showcase`](https://github.com/oleksandr-balyshyn/glyphora/tree/main/examples/showcase).
