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
- opening a modal removes everything below it from the tab order.

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

`MouseEvent` carries absolute terminal coordinates, a `MouseEventKind`, and keyboard
modifiers:

```scala
panel("Canvas")(canvasView).onMouseEvent {
  case MouseEvent(x, y, MouseEventKind.Down, _) =>
    selectedCell.set((x, y))
    true
  case MouseEvent(_, _, MouseEventKind.ScrollDown, _) =>
    zoom.update(value => math.max(1, value - 1))
    true
  case _ =>
    false
}
```

Kinds are `Down`, `Up`, `Drag`, `Moved`, `ScrollUp`, and `ScrollDown`. Coordinates
are absolute screen cells; custom widgets should compare them with the element's
known model or use built-in interactive elements when possible.

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

## Backend support

`TuiApp` and `JLine3Backend` negotiate mouse capture for you. A custom runner owns
that backend lifecycle itself. Input decoding belongs in the backend—application
code should never parse escape sequences.

Headless tests can click exact cells and post any mouse kind. See
[Testing](./testing#test-mouse-and-resize) for examples, and
[Unicode & accessibility](./unicode-and-accessibility) for keyboard-equivalent and
focus guidance.
