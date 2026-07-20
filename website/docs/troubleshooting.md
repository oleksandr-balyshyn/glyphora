---
title: Troubleshooting
---

# Troubleshooting

Start here when an application does not render, input is missing, or a native build
behaves differently from the JVM build.

## `UnsupportedTerminal` at startup

Glyphora needs a controlling TTY. Running from an IDE output panel, a redirected
pipe, or a headless CI process intentionally returns `UnsupportedTerminal` instead
of entering raw mode. Run the app in a real terminal:

```bash
./mill examples.showcase.run
```

For CI, inject `HeadlessBackend` and drive the application with `Pilot`; see
[Testing](./testing).

## Input or mouse events do not arrive

- Confirm the element is focusable and use `Tab` to move focus.
- Return `false` from a custom handler when the event should continue bubbling.
- Do not parse escape sequences in application code; `JLine3Backend` owns decoding.
- Mouse capture is enabled by `TuiApp`. A custom runner must enable it through its
  `Backend` lifecycle.

## Text alignment breaks with emoji or CJK

Use `CharWidth` for all terminal width calculations. Java/Scala string length counts
UTF-16 code units, not terminal cells. Widgets already use `CharWidth`; custom
widgets should do the same for clipping, wrapping, and cursor placement.

## A signal update throws off the render thread

Key handlers, mouse handlers, and `onTick` already run on the render thread.
Callbacks from futures, HTTP clients, or other threads must hop back with
`runOnRenderThread` or `runLater` before mutating a `Signal`.

## Native-image compilation fails

First confirm the JVM build and tests pass, then build the exact example:

```bash
./mill examples.showcase.nativeImage
```

Avoid runtime reflection and dynamic class loading. Glyphora's derivation APIs use
Scala 3 `Mirror` and macros at compile time specifically so no `reflect-config.json`
is required. See [Native binaries](./native-image).

## The screen is corrupted after a crash

The normal runner restores cooked mode, cursor visibility, mouse capture, and the
alternate screen in a `finally` path. If the process is killed ungracefully, run
`reset` in the shell to restore terminal state.

## Still stuck?

Search [existing issues](https://github.com/oleksandr-balyshyn/glyphora/issues), then
open a minimal reproduction including the glyphora version, terminal emulator, OS,
JVM, and whether the issue reproduces with `HeadlessBackend`.
