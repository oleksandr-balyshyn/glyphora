# Terminus API/DSL study — for glyphora

Source studied: `creativescala/terminus` (Scala 3, cross-platform JVM/JS/Native).
Modules: `core`, `core-ce`, `ui`, `ui-ce`, `examples`.

Terminus is really **two layers with different philosophies**:

1. **`core`** — a low-level, capability/effect-based ANSI terminal DSL (write/color/
   format/cursor/raw-mode). This is the part with the distinctive "Scala-native" flavour.
2. **`ui`** — a higher-level retained widget toolkit (Column/Row/Text/Button/TextInput)
   built on a **signals + capabilities** reactive model, with a `FullScreen` root and a
   Cats-Effect runner variant (`ui-ce`). This layer is architecturally *very close to
   glyphora* and is the more relevant comparison.

---

## 1. Programming model

### 1a. `core` — capabilities as Scala 3 context functions

The core model is **capability-based, direct-style**, built entirely on Scala 3
**context functions** (`?=>`) and **intersection types** (`&`). There is no monad, no
`IO`, no explicit state threading in the base library.

Each terminal ability is a `trait` in package `terminus` that produces methods returning
a *context function* over a matching `effect.*` capability:

```scala
// core/shared/.../Writer.scala
trait Writer:
  def write(string: String): effect.Writer ?=> Unit =
    effect ?=> effect.write(string)
  def flush(): effect.Writer ?=> Unit =
    effect ?=> effect.flush()

// core/shared/.../Reader.scala
trait Reader:
  def read(): effect.Reader ?=> Eof | Char =
    effect ?=> effect.read()
```

Styling and cursor control are **scoped/bracketed**: they take the "inner program" as a
context-function argument and re-establish the prior state on exit. The capability
*accumulates in the return type* via intersection:

```scala
// core/shared/.../Color.scala
trait Color:
  object foreground:
    def green[F, A](f: F ?=> A): (F & effect.Color) ?=> A =
      effect ?=> effect.foreground.green(() => f(using effect))

// core/shared/.../Format.scala
trait Format:
  object format:
    def bold[F, A](f: F ?=> A): (F & effect.Format) ?=> A =
      effect ?=> effect.format.bold(() => f(using effect))
    object underline:
      def curly[F, A](f: F ?=> A): (F & effect.Format) ?=> A = ...
```

The public entry point is the `Terminal` object, which **mixes every capability trait
together** and provides `run`:

```scala
// core/jvm/.../Terminal.scala
trait Terminal
    extends effect.AlternateScreenMode, effect.ApplicationMode, effect.Color,
      effect.Cursor, effect.Format, effect.Dimensions, effect.Erase,
      effect.KeyReader, effect.NonBlockingReader, effect.Peeker, effect.RawMode,
      effect.Reader, effect.Writer

type Program[A] = Terminal ?=> A     // <-- the central type alias

object Terminal extends AlternateScreenMode, ApplicationMode, Color, Cursor, ... :
  export JLineTerminal.*
```

`JLineTerminal.run` supplies the single capability instance to the whole program:

```scala
def run[A](f: Program[A]): A =
  val terminal = Terminal.apply
  val result = f(using terminal)   // one `using` injection for the entire program
  terminal.close()
  result
```

Key traits to note:
- **`type Program[A] = Terminal ?=> A`** is the idiom users actually write against.
- **Raw mode / alternate screen / cursor-hidden are bracketed context functions**, not
  flags: `raw { ... }`, `alternateScreen { ... }`, `cursor.hidden { ... }`. Cleanup is
  guaranteed by `try/finally` inside the JLine impl.
- **Keys** are a rich ADT: `Key(modifiers, code)` with `KeyCode` (Char, F(n), Up, Enter,
  …) and a big table of named constants (`Key.up`, `Key.controlQ`, `Key.shiftTab`) plus
  a `cats.Show[Key]`.

### 1b. `ui` — signals + capabilities + retained component tree

The UI layer is a **retained-mode reactive toolkit** and is the direct analogue of
glyphora. Its model:

- **Signals**: `Signal[A]` (read via `.peek` untracked or `.get(using Observe)` tracked),
  `WritableSignal[A]` (`.set`, `.update`), `Signal.constant`, `.map`. A `Computed`
  re-evaluates lazily when a tracked source changed. This is essentially the same
  fine-grained signal graph glyphora has.
- **Capabilities are passed as `using` context parameters**, not global. The reactive
  primitives come from a `React` capability; layout from `Layout`; events from `Event`;
  time from `Timer` (CE only):

```scala
// ui/shared/.../capability/React.scala
trait React:
  def signal[A](initial: A): WritableSignal[A]
  def computed[A](thunk: Observe ?=> A): Signal[A]
  def effect(thunk: Observe ?=> Unit): Unit
```

- **Components are functions taking a context-function body.** A component's body runs
  once at setup, receives the capabilities it needs as a *single intersection-typed
  context parameter*, and returns/【emits】children:

```scala
// ui/shared/.../component/Text.scala
object Text:
  def apply(size: Size, style: TextStyle => TextStyle = identity)(
      body: (Event & React) ?=> Signal[text.Text]
  )(using ctx: Layout): Unit = ...

// ui/shared/.../component/Column.scala
object Column:
  def apply(size: Size, style: LayoutProps => LayoutProps = identity)(
      body: (Event & Layout & React) ?=> Unit
  )(using ctx: Layout): Unit = ...
```

- **`FullScreen`** is the root. It takes a `(Layout & React) ?=> Unit` body, runs the
  interactive loop (`cursor.hidden { raw { alternateScreen { ... } } }`), reads keys,
  refreshes a terminal-size signal, and diffs frames. Note it *reuses the core bracketed
  capabilities* to enter the modes:

```scala
// ui/shared/.../FullScreen.scala
InteractiveTerminal.cursor.hidden {
  InteractiveTerminal.raw {
    InteractiveTerminal.alternateScreen {
      val terminalSize = WritableSignal(terminal.getDimensions)
      val step = eventLoop(terminalSize)
      def loop(): Unit =
        val input = InteractiveTerminal.readKey()
        terminalSize.set(terminal.getDimensions)
        if step(Event.Input(input)) then loop()
      loop()
    }
  }
}
```

- **Event handlers** are registered in the setup body via the `Event` capability
  (`ctx.onKey(Key.up)(...)`). Registering `onKey` makes a component focusable. Root keys
  (Tab/Shift-Tab/Ctrl-Q) are installed by the runtime.
- **Effects are batched**: a signal write marks subscribers stale; the event loop drains
  an effect queue once per event, producing at most one frame ("all signal writes made by
  one event produce at most one frame").

### 1c. The Cats-Effect variant (`-ce`)

`core-ce` and `ui-ce` are *thin* additions, not a rewrite. `ui-ce.FullScreen` mirrors
`ui.FullScreen` but its body additionally gets a `Timer` capability
(`(Layout & React & Timer) ?=> Unit`) providing `every(interval): Signal[Long]` and
`after(delay)(f)` — things only a runtime with a clock can offer. The app is otherwise
identical direct-style component code. The CE-ness is confined to the *runner*:

```scala
// ui-ce/jvm/.../Demo.scala
object demo extends IOApp.Simple:
  def run: IO[Unit] =
    Resource.make(IO(JLineTerminal.apply))(t => IO(t.close()))
      .use(terminal => DemoApp.make.run(terminal))
```

So: **direct-style capability model at the core; cats-effect only wraps the outer
resource/loop and adds a time capability.** Users writing views never touch `IO`.

---

## 2. Representative snippets (real code from the repo)

### core — styling reads as nested scoped blocks

```scala
// examples/.../ColorForegroundGreen.scala
Terminal.run(id, rows = 3) {
  Terminal.format.bold {
    Terminal.foreground.green {
      Terminal.write("This is Terminus!")
      Terminal.flush()
    }
  }
}
```

```scala
// examples/.../NestedFormat.scala  — nesting restores the outer style automatically
Terminal.foreground.yellow {
  Terminal.write("Yellow ")
  Terminal.foreground.green(Terminal.write("Green "))
  Terminal.write("Yellow ")
}
Terminal.write("Unstyled")
```

```scala
// examples/.../Format.scala
Terminal.format.invert(
  Terminal.format.underline.curly(
    Terminal.write("Inverted with curly underline")
  )
)
```

### core — a program is a `Program[A]` = `Terminal ?=> A`

```scala
// core/shared/.../example/Prompt.scala
def writeChoice(description: String, selected: Boolean): Program[Unit] =
  if selected then terminal.format.bold(terminal.write(s"> ${description}\r\n"))
  else terminal.write(s"  ${description}\r\n")

final def read(): Program[PromptKey] =
  terminal.readKey() match
    case Eof      => throw new Exception("Received an EOF")
    case key: Key => key match
      case Key(_, KeyCode.Enter) => KeyCode.Enter
      case Key(_, KeyCode.Up)    => KeyCode.Up
      case other                 => read()
```

### ui — a reactive component tree (native demo)

```scala
// ui/native/.../Example.scala  (interactiveDemo)
val fullScreen = FullScreen { ctx ?=>
  val levels   = ctx.signal(Vector.fill(channels.size)(maxLevel / 2))
  val selected = ctx.signal(0)

  Column(Size.fixed(40, channels.size + 4)) { ctx ?=>
    ctx.onKey(Key.up)    { selected.update(s => (s - 1 + n) % n) }
    ctx.onKey(Key.down)  { selected.update(s => (s + 1) % n) }
    ctx.onKey(Key.right) { val i = selected.peek; levels.update(...) }

    Text(Size.fixed(40, 1), _.withBox(_.withoutBorder).withContent(_.withBold)) {
      staticText("🎛  Equalizer")
    }
    channels.zipWithIndex.foreach { case ((name, colour), i) =>
      Row(Size.fixed(40, 1)) {
        Text(Size.fixed(12, 1), _.withBox(_.withoutBorder)) { ctx ?=>
          ctx.computed { text.Text((if selected.get == i then "▶ " else "  ") + name) }
        }
        ...
      }
    }
  }
}
fullScreen.run(NativeTerminal)
```

### ui-ce — the DemoApp (timers + signals + keys)

```scala
// ui-ce/shared/.../DemoApp.scala
FullScreen { ctx ?=>
  val count  = ctx.signal(0)
  val ticks  = ctx.every(100.millis)
  val status = ctx.signal("The one-shot timer has not fired yet…")
  ctx.after(5.seconds)(() => status.set("The one-shot timer fired ✓"))

  Column(Size.fixed(44, 4)) { ctx ?=>
    ctx.onKey(Key.up)(count.update(_ + 1))
    ctx.onKey(Key.down)(count.update(_ - 1))

    Text(Size.fixed(44, 1), _.withBox(_.withoutBorder)) { ctx ?=>
      ctx.computed {
        val frame = frames((ticks.get % frames.size).toInt)
        text.Text(s"$frame Spinning, no key presses needed")
      }
    }
    Text(Size.fixed(44, 1), _.withBox(_.withoutBorder)) { ctx ?=>
      ctx.computed(text.Text(s"Count: ${count.get}"))
    }
  }
}
```

### ui — fluent, copy-based style props (the other styling idiom)

```scala
// ui/shared/.../style/CellProps.scala
final case class CellProps(fg: Color = Color.Default, bold: Boolean = false, ...):
  def withForeground(color: Color): CellProps = copy(fg = color)
  def withBold: CellProps                      = copy(bold = true)
  def withoutBold: CellProps                   = copy(bold = false)
  def withUnderline(u: Underline): CellProps   = copy(underline = u)
```

Styling a component is a `Props => Props` transform passed at construction:

```scala
Text(Size.fixed(24, 3), _.withContent(_.withBold)) { staticText("Bold 💪") }
Text(Size.fixed(24, 3),
     _.withBox(_.withBorderProps(CellProps(fg = Color.Red)))
      .withContent(CellProps(fg = Color.Red, bold = true))) { staticText("Red") }

// TextInputStyle has state variants baked in:
TextInputStyle.default
  .withBox(_.withBorderProps(CellProps(fg = Color.BrightBlack)))
  .focused(_.withBox(_.withBorderProps(CellProps(fg = Color.White, bold = true))))
```

Note the **two distinct styling grammars** in terminus:
- **core**: verbs-as-scopes — `format.bold { ... }`, `foreground.green { ... }`.
- **ui**: `withX`/`withoutX` on immutable `*Props` case classes + `.focused`/`.disabled`
  state overlays, threaded as `Props => Props` builder functions.

---

## 3. DSL idioms worth emulating

1. **Capabilities via `using`/`?=>`, accumulated in intersection types.** A program's
   *type* records what it touched (`(F & effect.Color) ?=> A`). Users never pass a
   terminal handle explicitly; `run` injects one `using` instance for the whole tree.
   This is the signature "Scala-native" move.

2. **A single, memorable program alias.** `type Program[A] = Terminal ?=> A`. Everything
   the user writes has this shape. Enormous for discoverability.

3. **Scoped effects instead of flags.** `raw { }`, `alternateScreen { }`,
   `cursor.hidden { }`, `format.bold { }` — enter/restore is structural and leak-proof
   (`try/finally`). Styling *nests* and auto-restores the parent style.

4. **Verbs live under grouping objects.** `foreground.green`, `background.red`,
   `format.underline.curly`, `cursor.to(x,y)`, `cursor.up()`. Discoverable by typing the
   namespace and letting completion list the rest. Reads like prose.

5. **Direct-style first, monad optional.** The base API is plain synchronous Scala. The CE
   variant *adds* capabilities (`Timer`) and wraps only the outer resource/loop — it does
   not force `IO` into view code. Effect purity is a *runner* concern.

6. **`withX` immutable-copy props + state overlays.** `CellProps.withBold`,
   `Style.focused(_. ...)`, `TextInputStyle.disabled(_. ...)`. Composable, allocation-
   cheap, pattern-matchable, testable.

7. **Rich, named key vocabulary.** `Key.up`, `Key.controlQ`, `Key.shiftTab`, plus
   `Key.control('a')` constructors and a `Show` instance. Handlers read
   `ctx.onKey(Key.up)(...)` — no raw escape-code matching in app code.

8. **Setup-vs-render separation enforced by the type system.** `React` is available only
   in setup bodies; a `@implicitNotFound` explains *why* you can't create a signal inside
   a render pass. Capabilities double as guard rails.

9. **Components as `(Caps) ?=> Body` builder functions**, sized/styled by leading params
   and populated by a trailing block: `Column(size, style){ body }`. Uniform shape across
   every component.

---

## 4. How glyphora compares

glyphora and terminus-`ui` **already share the core architecture**: retained element/
component tree, fine-grained `Signal`/`Computed` with tracked reads, "read it in the view
and it re-renders", batched redraw, focus/mouse routing, `FullScreen`/`TuiApp` root,
JLine backend, headless test backend. The reactive semantics are nearly identical
(glyphora `count.get` under `ReactiveScope` == terminus `signal.get(using Observe)`).

Where they **echo each other already**:

| Concern | terminus | glyphora |
|---|---|---|
| Reactive state | `Signal`/`WritableSignal`, `.peek`/`.get`, `.update`/`.set`, `.map` | `Signal`/`Computed`, `.peek`/`.get`, `.update`/`.set` — same names |
| Tracked-read context | `Observe` (`using`) | `ReactiveScope` (`using`) |
| Root | `FullScreen { ... }` / CE runner | `TuiApp` trait with `view(using ReactiveScope)` |
| Factories | `Column(...)`, `Row(...)`, `Text(...)`, `Button(...)` | `column(...)`, `row(...)`, `panel(...)`, `text(...)`, `button(...)` |
| Keys | `Key` ADT + named constants | `KeyEvent`/`KeyCode` ADT |
| Layout sizing | `Size`/`Measurement` (`fixed`, `Percentage`, `Weight`, `WrapContent`) | `Constraint` (`Length`, `Percentage`, `Fill`, `Min`, `Max`) via `.length/.percent/.fill` |
| Styling | `CellProps.withBold`/`withForeground` builder | `Style` + fluent `.bold/.color(...)` extensions |

Where they **diverge** (glyphora's choices, mostly good):

- **glyphora styling is post-hoc fluent extensions on `Element`**
  (`text("x").bold.color(Color.Green).dim`), chaining left-to-right. terminus-ui uses
  `Props => Props` transforms passed *into* the constructor
  (`Text(size, _.withContent(_.withBold))`). glyphora's reads more naturally for the
  common case; terminus's keeps styling and construction unified and supports state
  overlays (`.focused`, `.disabled`) that glyphora expresses via a theme `focusStyle`
  patch instead.
- **glyphora uses lowercase package-level factory functions** re-exported through one
  `import io.worxbend.tui.dsl.*`; terminus uses **capitalised `object.apply` companions**
  (`Column`, `Text`). glyphora's "one import to rule them all" is friendlier; terminus's
  namespacing aids completion.
- **glyphora threads capability implicitly through `ReactiveScope` only**; terminus splits
  fine-grained capabilities (`React`, `Layout`, `Event`, `Timer`) and passes them as an
  intersection `using` param per component body. glyphora instead uses per-element
  `.onKeyEvent`/`.onMouseEvent`/`.focusable` builder methods and an app-level
  `bindings`/`KeyBindings` registry — arguably simpler for app authors.
- **glyphora's event model is bubbling from focused element + app bindings + command
  palette**; terminus registers handlers in setup and dispatches to the focused component.
  glyphora is richer here (palette, screens, toasts, splash, chrome presets — none of
  which terminus has).
- **glyphora has no direct-style low-level "core DSL"** equivalent to terminus's
  `Terminal.foreground.green { write(...) }`. glyphora goes straight to widgets; the raw
  ANSI scoped-styling grammar has no glyphora counterpart (and probably doesn't need one).

Net: glyphora is roughly "terminus-`ui` + app chrome + more widgets + a fluent styling
skin", already close in spirit. The gaps that would make it *feel* more terminus-like are
ergonomic, not architectural.

---

## 5. Concrete recommendations for glyphora's API

These preserve glyphora's signals/retained-tree architecture; none require a rewrite.

**A. Publish a `Program`/`View` type alias for the view shape.**
terminus's `type Program[A] = Terminal ?=> A` is a cornerstone of its feel. glyphora's
`def view(using ReactiveScope): Element` is the same idea unnamed. Add:
```scala
type View = ReactiveScope ?=> Element        // in io.worxbend.tui.dsl
```
Then `def view: View` and any helper `def sidebar: View` read uniformly, and users learn
one shape. Sub-views compose as `def header(using ReactiveScope): Element` today but a
named alias makes it teachable.

**B. Adopt terminus's scoped-styling grammar as an *optional* alternative to chained
extensions.** glyphora's `.bold.color(c)` is great for a single element. For a *subtree*,
terminus's `foreground.green { ... }` is nicer than styling each leaf. Consider a
`styled(_.bold.color(Color.Green)) { column(...) }` block helper (or a `withStyle`
container element) that pushes a default style onto descendants — mirroring terminus's
auto-restoring nested style scopes, but as a retained node.

**C. Add named key-chord constants and an `onKey(Key.X)` sugar.** terminus's
`ctx.onKey(Key.up)(...)` and `Key.controlQ` are far more readable than glyphora's
`case KeyEvent(KeyCode.Char('+'), _) => ...; true` boilerplate (see `CounterApp`). Provide:
```scala
object Key:
  val up = KeyEvent(KeyCode.Up); val ctrlQ = KeyEvent(KeyCode.Char('q'), Ctrl); ...
extension (e: Element)
  def onKey(k: KeyEvent)(handler: => Unit): Element =   // auto-returns true
    e.onKeyEvent(ev => if ev == k then { handler; true } else false)
```
This removes the `=> true` / `case _ => false` ceremony from the common path while keeping
`onKeyEvent` for advanced matching.

**D. Offer `.focused`/`.disabled` style-state overlays on `Style`/elements**, matching
terminus's `TextInputStyle.focused(_. ...)`. glyphora already has `theme.focus` patching;
exposing a per-element `.whenFocused(_.bold)` extension would let authors override focus
styling locally without reaching into the theme.

**E. Keep the fluent extension styling — it's a genuine improvement over terminus.**
`text("x").bold.color(Color.Cyan)` reads better than
`Text(size, _.withContent(_.withBold.withForeground(Color.Cyan)))`. Don't regress to
constructor-threaded props. But *do* borrow terminus's `withX`/`withoutX` symmetry on the
underlying `Style` so both `.bold` and an explicit `.notBold`/`.styled(_.withoutBold)`
exist for programmatic style manipulation.

**F. Consider a lightweight `Timer`-style capability for animation**, echoing
`ui-ce`'s `ctx.every(100.millis): Signal[Long]` and `ctx.after(5.seconds)(...)`.
glyphora currently drives animation through `onTick()` + manual signal advances and the
`Effect` engine. A signal-returning `every(...)` would let animated views be declared
purely reactively (`spinner(frame = ticks.get % n)`) instead of mutating state in an
`onTick` callback — a more terminus-like, more compositional story.

**G. Standardise the "sized + styled + body" constructor shape** for containers, à la
terminus `Column(size, style){ body }`. glyphora containers take `children*` and then
you post-fix `.length(...)`. That's fine, but documenting the canonical order
(`row(...).fill`, `panel("t")(...).rounded`) as *the* idiom — the way terminus makes
`Component(size, style){ body }` universal — improves learnability.

**H. Lean into the single-import ergonomics you already have** — this is one place
glyphora is *ahead* of terminus. `import io.worxbend.tui.dsl.*` giving every factory +
styling + core vocab is exactly the frictionless on-ramp terminus lacks (it needs
`import terminus.*` plus knowing the `Terminal.` prefix). Keep and advertise it.

### Priority

- **High, cheap, high-impact:** C (named keys + `onKey` sugar), A (`View` alias).
- **Medium:** B (subtree style scopes), F (`every`/`after` animation signals).
- **Low / polish:** D, E-symmetry, G (docs), H (already done — just document).

The throughline: glyphora's *architecture* already matches terminus-`ui`. To "feel like
terminus", invest in the **surface grammar** — named key constants, an `onKey` that
swallows the `true`/`false` ceremony, a named view-type alias, and optional scoped
styling — rather than changing the reactive core.
