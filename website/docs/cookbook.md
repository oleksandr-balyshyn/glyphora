---
title: Cookbook
---

# Cookbook

Short recipes for the common shapes. Every snippet assumes
`import io.worxbend.tui.dsl.*`; the [examples](./examples) are complete runnable
versions of these patterns.

## A minimal app

```scala
object Hello extends TuiApp:
  def view(using ReactiveScope): Element =
    panel("Hello")(text("Welcome!").bold.color(Color.Cyan)).rounded
  override def bindings = KeyBindings(binding("q", "quit")(quit()))
```

## State: signals, not threading

State lives in `Signal`s; any signal the view *reads* re-renders it when set.

```scala
val count = Signal(0)
def view(using ReactiveScope) = text(s"count: ${count.get}")
// in a binding/handler (render thread): count.update(_ + 1)
```

More in [State & signals](./state-and-signals).

## The app shell

```scala
scaffold(
  topBar = Some(topBar("myapp", tabs = Seq("Files", "Logs"), selectedTab = tab.get)),
  sidebar = Some(sidebar(directoryTree(treeState), width = 28)),
  statusBar = Some(statusBar(bindings)),
)(content)
```

`Theme.Dark`/`Light`/`HighContrast` are ambient (`given Theme`); override `TuiApp.theme`
and re-render to switch at runtime. More in [The app shell](./app-shell).

## Keys once, everywhere

```scala
override def bindings = KeyBindings(
  binding("ctrl+s", "save")(save()),
  binding("?", "help")(pushScreen(Screen(helpOverlay(bindings)))),
  binding("esc", "quit")(quit()),
)
```

One declaration drives dispatch, the status-bar hints, the `?` overlay, and the
`Ctrl+P` command palette (fuzzy-filtered, Enter runs the command).

## Navigation, dialogs, toasts

```scala
pushScreen(Screen { centered(40, 7)(panel("Confirm")(...)) }) // modal: base loses focus
pushScreen(Screen.full(settingsView))                         // replaces the view
popScreen()
notify("Saved", ToastLevel.Success)                           // needs config.tickRate
```

## Splash & animation

```scala
override def splash = Some(SplashScreen(
  centered(36, 5)(bigText("MYAPP").color(Color.Cyan)),
  effect = Effect.coalesce(800.millis),
))
runEffect(Effect.parallel(Effect.fadeIn(300.millis), Effect.sweepIn(300.millis)))
```

Effects are post-render buffer transforms; compose with `sequence`/`parallel`/
`delay`/`repeat` and `Easing`. `Tween` animates plain values from `onTick`. More in
[Motion](./motion).

## Forms with compile-time derivation

```scala
final case class Signup(username: String, age: Int, subscribe: Boolean)
val form = FormState.of(
  deriveForm[Signup],
  Field.int("age").mapValidated(a => if a >= 18 then Right(a) else Left("must be 18+")),
)
// view: Form(form)   submit: form.submit()   result: form.result / form.errors
```

## Testing headlessly

```scala
val backend = HeadlessBackend(Size(60, 16))
val pilot = Pilot.start(backend) { app.runWith(backend) }
pilot.typeText("hi").pressKey(KeyCode.Enter).waitForIdle()
assert(pilot.screenText.contains("hi"))
```

More in [Testing](./testing).

## Charts

```scala
chart(Seq(Dataset("cpu", points)), xBounds = (0, 60), yBounds = (0, 100))
  // smoother: Chart(..., resolution = CanvasResolution.Braille, showLabels = true)
pieChart(Seq("a" -> 3.0, "b" -> 1.0)); heatmap(grid); stackedBarChart(series)
image(Image.fromFile(path).toOption.get)  // half-block raster
```
