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

  def view(using ReactiveScope, Theme): Element =
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
  sidebar = Some(sidebar(sidebarView, width = 24, side = Side.Left)),
  statusBar = Some(statusBar(bindings)),
)(mainView)
```

- `topBar` renders a title, optional tabs, and optional right-aligned status.
- `sidebar` describes content, width, and `side` (`Side.Left` or `Side.Right`);
  `scaffold` handles placement.
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
opening functionality.” `KeyEvent.parse` — the one parser in `tui-core` that reads
these strings — accepts printable keys, named keys, and modifiers such as `ctrl+s`,
`alt+enter`, and `shift+tab` (which also answers to `backtab`, the name most terminals
give that key). `Pilot.press` in a test takes the same strings, so a
test presses what the app declared rather than a hand-translated `KeyEvent`.

Two characters are both syntax and keys, and the parser resolves each in favour of the
key:

- `+` separates a modifier from the key it modifies, but only when it terminates a
  modifier name — so `"+"` binds the plus key and `"ctrl++"` binds Ctrl+plus.
- Surrounding whitespace is stripped as formatting, but never all of it — so `" "`
  binds the space bar, as does the more readable `"space"`.

Modifier names and named keys are case-insensitive (`"Ctrl+Enter"` works), but a
single-character key keeps its case, because that case is what the terminal reports:
Shift+G arrives as `KeyCode.Char('G')`, so bind `"G"`, not `"shift+g"`. Ctrl is the
exception — a terminal cannot tell Ctrl+S from Ctrl+Shift+S, so `"ctrl+S"` folds to
`"ctrl+s"` rather than declaring a binding that could never fire.

Five Ctrl combinations are rejected outright rather than parsed, because a terminal
without the kitty keyboard protocol has no way to send them — the control code they
would produce is already spoken for:

| Rejected spec | What the terminal actually sends | Bind this instead |
|---|---|---|
| `"ctrl+i"` | Tab | `"tab"` |
| `"ctrl+m"` | Enter | `"enter"` |
| `"ctrl+j"` | Enter | `"enter"` |
| `"ctrl+h"` | Backspace | `"backspace"` |
| `"ctrl+["` | Escape | `"esc"` |

Before, those specs parsed happily and then never fired, which is a bug that looks like
a broken key. Now they fail loudly at declaration time and the message names the
replacement.

Function keys run `"f1"` through `"f35"`, matching the range the input decoder emits.
How far up the range your terminal can actually reach depends on what it sends:
`"f1"`–`"f12"` work everywhere; `"f13"`–`"f20"` work on xterm-family terminals, which
report them as the legacy `CSI 25~`–`CSI 34~` numbers (that is what the keyboard sends
for Shift+F1 through Shift+F8); `"f21"`–`"f35"` need a terminal speaking the kitty
keyboard protocol, because no legacy escape sequence names them.

Six more keys have spec names, and they come with a caveat worth reading before you use
one:

| Spec | Key |
|---|---|
| `"capslock"` | Caps Lock |
| `"scrolllock"` | Scroll Lock |
| `"numlock"` | Num Lock |
| `"printscreen"`, `"prtsc"` | Print Screen |
| `"pause"` | Pause / Break |
| `"menu"` | the context-menu key beside the right-hand Ctrl |

An ordinary terminal never sends any of these. They arrive only from a terminal speaking
the kitty keyboard protocol (`"menu"` also arrives from xterm, which has sent it as
`CSI 29~` for decades). So treat a binding on one as a bonus shortcut for the people
whose terminal supports it, and always give the same command an ordinary key as well —
the `binding(Seq(…), …)` form below is exactly for that.

The three lock keys report the key *being pressed*, not the state it leaves behind.
glyphora has no notion of whether Caps Lock is currently on, and nothing is delivered
when the light changes for any other reason.

A spec that names no key (`"ctrl+"`) or no known key (`"banana"`) is a programmer error
and throws from `binding` at declaration time.

## Modifier keys held on their own

A modifier is normally visible only as part of another key's event: holding Ctrl and
pressing `a` arrives as `KeyCode.Char('a')` with the `Ctrl` bit set in
`event.modifiers`, and the Ctrl key itself produces nothing. That is what almost every
application wants, and it is the only thing a terminal without the kitty keyboard
protocol can report.

Some terminals can also report a modifier press as a key in its own right. When they
do, it arrives as `KeyCode.Modifier(key)`, where `key` is a `ModifierKey` —
`LeftShift`, `RightControl`, `LeftAlt`, and so on, with left and right kept apart
because the protocol distinguishes them. Read it from an element or app key handler if
you want to show a “Ctrl held” hint:

```scala
.onKeyEvent { event =>
  event.code match
    case KeyCode.Modifier(ModifierKey.LeftControl) => showCtrlHint(); true
    case _                                         => false
}
```

There is deliberately no key spec for a bare modifier: `"shift"` is not a valid key
name, so declaring a binding on it fails at declaration time the same way `"banana"`
does. A binding on a bare modifier would fire part-way through every chord that begins
with that modifier, which is never what the author meant.

Note what the terminal has to do for any of this to happen: the modifier press must be
reported, and glyphora currently asks a kitty-capable terminal only to disambiguate
escape codes, not to report every key. So on a terminal glyphora set up itself these
events do not arrive today; a terminal already put into report-all-keys mode by
something else will deliver them, and the decoder now names them correctly instead of
dropping them on the floor.

## One command, several keys

When a command answers to more than one key — a vim-flavoured app where `j` and the
down arrow both mean “next” — declare it once with a sequence of specs instead of
twice:

```scala
override def bindings = KeyBindings(
  binding(Seq("down", "j"), "next item")(selectNext()),
  binding(Seq("up", "k"), "previous item")(selectPrevious()),
)
```

Any of the listed keys fires the action, and the **first** spec is the label, so the
status bar shows one `down next item` hint, the help overlay lists one row, and the
palette offers one command. Two separate `binding(…)` declarations would fire
correctly but advertise the same command twice everywhere it is listed.

### Media and transport keys

The play, pause, next-track and volume buttons have spec names too:

| Spec | Key |
|---|---|
| `"play"`, `"mediapause"`, `"playpause"` | play, pause, and the single play/pause toggle |
| `"stop"`, `"record"`, `"reverse"` | stop, record, reverse |
| `"fastforward"`, `"rewind"` | fast forward, rewind |
| `"tracknext"` (`"next"`), `"trackprevious"` (`"trackprev"`, `"prev"`) | skip forward, skip back |
| `"volumeup"` (`"volup"`), `"volumedown"` (`"voldown"`), `"mute"` | volume |

Note `"mediapause"`, not `"pause"`: `"pause"` names the Pause/Break key above the arrow
cluster, which is a different physical key.

Two things have to go right for one of these to reach your app. The terminal has to
speak the kitty keyboard protocol, and the desktop environment has to not take the key
first — many take the volume and transport keys before any application sees them. So
pair every media binding with an ordinary key, using the several-keys form above:

```scala
override def bindings = KeyBindings(
  binding(Seq("playpause", "space"), "play or pause")(togglePlayback()),
  binding(Seq("tracknext", "n"), "next track")(skipForward()),
)
```

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
from tab order and from event routing: nothing below a modal receives a key or a mouse
event. `Screen.full(...)` replaces the current view entirely:

```scala
pushScreen(Screen.full(settingsPage))
```

Call `popScreen()` from the active screen to return. Focus stays inside a modal by
construction, so you do not need a separate focus trap.

Which of the two a screen is, is its `presentation`: `Presentation.Modal` (the default)
or `Presentation.Full`. A hand-written `Screen` overrides it directly:

```scala
val settings = new Screen:
  def view(using ReactiveScope, Theme): Element  = settingsPage
  override def presentation: Presentation = Presentation.Full
```

## Notify without interrupting flow

```scala
notify("Deployment queued", NoticeLevel.Success)
notify("Authentication expired", NoticeLevel.Error, duration = 10.seconds)
dismissToasts()
```

Toasts stack in the top-right corner and expire after a wall-clock `duration`
(three seconds when you do not say). Each one renders through the same `Notice` widget
the rest of the toolkit uses, so it carries that severity's icon and theme colour.

Ticks are what *notices* that a toast has expired, so an app that uses them needs a
tick rate — but the tick rate no longer decides how long "three seconds" is:

```scala
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
  border: Style,          // the frame `panel` and `rule` draw with
  focus: Style,           // the focus cue, and every element's selection highlight
  loading: LoadingTheme,  // spinners and progress bars
  markdown: MarkdownTheme,// headings, bullets, quotes, inline code in `markdown(…)`
  syntax: SyntaxTheme,    // token colours for the standalone `SyntaxHighlighter`
)
```

`Theme.Dark`, `Theme.Light`, and `Theme.HighContrast` ship with glyphora. Built-in
chrome, palette, focus, and toasts use semantic theme roles rather than hardcoded
colors, and so do the themed element factories: `panel` and `rule` take their frame
from `border`, `dialog` from `primary` and `focus`, every "pick a row" element —
`list`, `tree`, `menu`, `selectionList`, `filePicker`, `directoryTree` — highlights the
selected row with `focus`, and `markdown` renders through `markdown`. That last one is
the reason the field exists: the widget-level `MarkdownTheme()` defaults are tuned for
a dark terminal, so a document rendered under `Theme.Light` used to come out with cyan
headings on white.

The three grouped sub-palettes exist because their fields retheme together.
`LoadingTheme.from(accent, muted, surface)` derives a coherent loading palette for a
custom theme rather than making you spell out all five — see
[Widgets](./widgets#theming-the-animations).

### How a themed helper gets the theme

`view` takes it: `def view(using ReactiveScope, Theme): Element`. Everything it calls
that needs a theme — `statusBar(bindings)`, `topBar(...)`, `panel(...)` — resolves
against the app's own `theme` because the compiler passes it down that `using`
parameter. There is no `given` to declare.

That signature is not decoration. A framework that installed `given Theme = theme`
*around* the call to `view` would leave it out of scope *inside* the body, so an app
that overrode `theme` and wrote `statusBar(bindings)` in its own view would silently
get `Theme.Dark`. Carrying it in the type is what makes the override reach.

For live switching, keep a theme index in a `Signal`, return the selected value from
`theme`, and read the index in `view` so the tree is invalidated. A complete snippet
lives in [State & signals](./state-and-signals#runtime-theme-switching).

## Add a splash or frame effect

```scala
import scala.concurrent.duration.*

override def splash = Some(
  SplashScreen(
    content = centered(38, 5)(bigText("GLYPHORA").fg(Color.Cyan)),
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
handlers or from `onStart()` — never from a constructor.

## Choose the terminal to draw on

`run()` opens a terminal through `createBackend()`, which by default is JLine on the
process's controlling terminal. Two overrides change that without giving up `main`:

```scala
object MyApp extends TuiApp:
  // force a palette instead of sniffing NO_COLOR / COLORTERM / TERM — the way to
  // check that a theme still reads on a sixteen-color terminal without finding one
  override protected def colorDepth: ColorDepth = ColorDepth.Ansi16

  def view(using ReactiveScope, Theme): Element = ...
```

Override `createBackend()` itself to substitute a different `Backend` entirely — a
recording backend, a remote one — and everything else about the app is unchanged,
because the loop lives in `runWith(backend)` and runs over whatever it is handed.
That is the same seam headless tests use; see [Testing](./testing).

## Lifecycle hooks

| Hook | When to use it |
|---|---|
| `config` | tick cadence and backend behavior |
| `colorDepth` | force a palette instead of detecting one from the environment |
| `createBackend()` | draw on something other than JLine's controlling terminal |
| `theme` | active semantic theme |
| `bindings` | app-wide commands |
| `splash` | optional launch composition |
| `onStart()` | the run has begun and this is the render thread — start pollers and timers here |
| `onStop()` | the run is over, whatever ended it — cancel what `onStart` began |
| `onTick()` | advance frame-oriented state; never block |
| `onResize(size)` | react to a new terminal size (clamp a scroll offset, re-fetch a page) |
| `onTerminalFocus(focused)` | pause/resume activity when mode-1004 focus events arrive |
| `onInterrupt()` | intercept `Ctrl+C`; return `true` to keep running |

### Start and stop

`onStart()` runs on the render thread after the terminal is ready and before the first
frame. That matters for background work: `Async` captures the render loop of the
thread that calls it, and your app object is constructed long before any loop
exists — so `Async.every(...)` in a field initialiser attaches to no loop at all and
its results are discarded forever. Calling `quit()` from `onStart()` exits before
anything is drawn, which is how a start-up check declines to run.

`onStop()` runs on the way out of every exit path: `quit()`, an unconsumed `Ctrl+C`, a
backend failure, an event handler that threw. Nothing cancels a repeating `Async.every`
for you, so this is where it is cancelled.

```scala
private var poller: Option[Cancelable] = None

override def onStart(): Unit = poller = Some(Async.every(5.seconds)(refresh()))
override def onStop(): Unit  = poller.foreach(_.cancel())
```

By the time `onStop()` runs the terminal has already been handed back, so `quit()`,
`suspend`, `printAbove` and `copyToClipboard` are no-ops there — it is for your own
resources, not for the screen.

### Ask for a frame

A `Signal` write schedules its own redraw, and an event handler that returns `true`
already asks for one. Neither covers state the reactive layer cannot see: the
caller-owned widget states (`ListState`, `TextInputState`, `DataTableState`, `LogState`)
are plain mutable objects, so

```scala
Async.run(fetchRows())(rows => tableState.rows = rows)
```

updates the table correctly and still leaves the previous frame on screen until the
user happens to press a key. `requestRedraw()` asks for the frame that mutation earned:

```scala
Async.run(fetchRows()) { rows =>
  tableState.rows = rows
  requestRedraw()
}
```

The full shell in action is
[`examples/showcase`](https://github.com/oleksandr-balyshyn/glyphora/tree/main/examples/showcase).
