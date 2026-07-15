---
title: Mouse & focus
---

# Mouse & focus

## Focus

Focusable elements (inputs, buttons, list-like widgets) join a tab order built by
walking the `Element` tree. `Tab` / `Shift+Tab` move focus; the focused element
renders with `theme.focus` (reverse video by default).

- A **modal** [`Screen`](./app-shell#screens-toasts-splash) suppresses focus on
  everything beneath it, so `Tab` only cycles within the modal while it's open.
- Key routing is depth-first with stop-propagation bubbling: `.onKeyEvent { ... }`
  handlers return `true` to consume an event (stopping it there) or `false` to let it
  bubble to the parent.

```scala
text("q to quit").onKeyEvent {
  case KeyEvent(KeyCode.Char('q'), _) => quit(); true
  case _                              => false
}
```

## Mouse

`.onMouseEvent { ... }` follows the same consume/bubble contract as keys. Built-in
interactive widgets already wire this up (a `builtinMouseHandler`) so click-to-focus,
click-to-activate, wheel-to-scroll, and drag (sliders, split panes) work without extra
code — you only need `.onMouseEvent` for custom hit-testing.

```scala
panel("Click me")(text("hi")).onMouseEvent {
  case MouseEvent(MouseEventKind.Down(MouseButton.Left), _, _) => notify("clicked!"); true
  case _                                                       => false
}
```

Mouse capture is a `Backend` concern (see [Architecture](./architecture#tui-terminal))
— `JLine3Backend` enables it automatically when a `TuiApp` runs.
