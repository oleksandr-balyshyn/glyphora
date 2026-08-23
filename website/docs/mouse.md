---
title: Mouse & focus
description: Understand glyphora's focus order, key bubbling, mouse hit-testing, built-in interactions, and stable focus identity.
---

# Mouse & focus

Keyboard and mouse input meet in the same element tree. Focus determines where key
events begin; rendered rectangles determine where mouse events land. User handlers
run before built-in widget behavior, and an event can bubble toward its parent when
it is not consumed.

## How focus is built

On each render glyphora walks the current `Element` tree depth-first and records
focusable elements. Inputs, buttons, toggles, selects, lists, text areas, and other
interactive elements opt in automatically.

- `Tab` moves to the next focusable element;
- `Shift+Tab` moves to the previous one;
- clicking an interactive element focuses it first;
- the active theme's `focus` style decorates the focused element;
- opening a modal removes everything below it from the tab order and from key and mouse routing.

Make any custom element focusable with `.focusable`:

```scala
panel("Custom control")(text("press Enter"))
  .focusable
  .onKey(Key.Enter) { activate() }
```

## Keep focus stable across changing trees

Without an explicit key, focus is positional. Conditional content inserted before a
control can make the same numeric position refer to a different element. Assign a
stable identity when tree shape changes:

```scala
column(
  input(search, placeholder = "search").key("search"),
  if showAdvanced.get then input(pattern).key("advanced-pattern")
  else spacer(1),
  button("Apply") { apply() }.key("apply"),
)
```

During the next focus pass glyphora finds the same key in the new tree and moves the
focus index to it.

## Use concise handlers for exact keys

`.onKey` consumes an event only when one of its keys matches and composes with other
`.onKey` calls:

```scala
panel("Counter")(text(count.get.toString))
  .onKey(Key.char('+'), Key.Up) { count.update(_ + 1) }
  .onKey(Key.char('-'), Key.Down) { count.update(_ - 1) }
  .onKey(Key.Home) { count.set(0) }
```

The `Key` vocabulary includes arrows, `Enter`, `Escape`, `Tab`, paging and editing
keys, `f(1)` through `f(12)`, `ctrl`, `alt`, `shift`, and common constants such as
`CtrlS`, `CtrlP`, and `CtrlQ`.

## Use raw key events for conditional consumption

Drop to `.onKeyEvent` when you need the event's modifiers or want to decline it:

```scala
text("q quits only from this mode").onKeyEvent {
  case KeyEvent(KeyCode.Char('q'), _) if canQuit.peek =>
    quit()
    true
  case _ =>
    false // bubble to the parent, then app bindings
}
```

For the focused path, routing order is:

1. focused element's user handler;
2. focused element's built-in behavior;
3. each ancestor's user handler, inner to outer;
4. the app's `KeyBindings`;
5. built-ins such as `Tab`, `Ctrl+P`, and unconsumed `Ctrl+C`.

This lets an input consume normal text and editing keys while a parent still handles
`Escape` and the app still handles global commands.

## Handle custom mouse behavior

`MouseEvent` carries a `Position` (the absolute terminal cell the pointer was over), a
`MouseEventKind`, and keyboard modifiers:

```scala
panel("Canvas")(canvasView).onMouseEvent {
  case MouseEvent(Position(x, y), MouseEventKind.Down, _) =>
    selectedCell.set((x, y))
    true
  case MouseEvent(_, MouseEventKind.ScrollDown, _) =>
    zoom.update(value => math.max(1, value - 1))
    true
  case _ =>
    false
}
```

Kinds are `Down`, `Up`, `Drag`, `Moved`, `ScrollUp`, and `ScrollDown`. The position is
in absolute screen cells and zero-based, the same coordinate space a `Rect` uses — so a
handler that wants coordinates relative to its own area subtracts that area's origin
(`event.position.x - area.x`). Custom widgets should compare the position with the
element's known model, or use built-in interactive elements when possible.

## Built-in mouse behavior

You do not need custom handlers for common interactions:

| Element | Mouse behavior |
|---|---|
| input, list, select, controls | click focuses; click may position/select/activate |
| button, checkbox, toggle | click activates |
| scroll view and scrollable lists | wheel changes offset |
| slider | click or drag positions the value |
| split pane | drag moves the divider |

A user `.onMouseEvent` runs first. Return `false` when the widget's built-in behavior
should still run.

## Mouse delivery order

Mouse events are delivered only to elements that actually rendered under the
pointer, innermost first: the deepest handler-carrying element under the pointer,
outward to the element the click resolved to, then that element's ancestors. At each
step the element's own `.onMouseEvent` runs first and its built-in behavior second,
so a **wheel** a built-in declines keeps travelling outward — that is how the wheel
still scrolls a `scrollView` when the pointer sits over a button inside it. Only the
wheel does that: scrolling targets the nearest scrollable ancestor, while a press or a
drag stays with the element it landed on, so dragging inside a `splitPane` pane does
not move the divider. An element's built-in only ever acts on a pointer inside that
element's own rendered area. Each handler sees a given event at most once — returning
`false` bubbles the event outward, it never redelivers it. A handler on an element the
pointer is not over is not called.

Where overlapping elements *carrying handlers* compete, the topmost one wins.
Containers paint children in order and `layers(base, overlays*)` paints later children
over earlier ones, so a click on an overlay goes to the overlay, not to the base drawn
underneath it. Focusable elements are resolved differently — by smallest covering area,
which has no notion of z-order — so give an overlay's controls their own layer rather
than relying on paint order to shadow a focusable beneath them.

## Backend support

`TuiApp` and `JLine3Backend` negotiate mouse capture for you. A custom runner owns
that backend lifecycle itself. Input decoding belongs in the backend—application
code should never parse escape sequences.

Headless tests can click exact cells and post any mouse kind. See
[Testing](./testing#test-mouse-and-resize) for examples, and
[Unicode & accessibility](./unicode-and-accessibility) for keyboard-equivalent and
focus guidance.
