---
title: FAQ
description: Answers about glyphora's scope, modules, terminals, state model, testing, Unicode, accessibility, reflection, and release status.
---

# Frequently asked questions

Short answers to architectural and adoption questions. For symptoms and fixes, use
[Troubleshooting](./troubleshooting).

## What kinds of applications is glyphora for?

Interactive full-screen or region-based terminal programs: dashboards, deployment
tools, file browsers, forms, monitors, database clients, developer utilities, and
focused CLI companions. It is not a line-oriented argument parser or shell-command
framework; pair it with one if your program also has non-interactive commands.

## Is glyphora a Scala port of another TUI framework?

No. It borrows proven ideas—immutable descriptions, buffer rendering, reactive
state, and backend isolation—but its API is designed around Scala 3 capabilities,
compile-time derivation, and GraalVM constraints.

## Which module should an application depend on?

Use `tui-dsl` for normal applications. It transitively includes core, widgets,
runtime, terminal integration, and macros. Depend on a lower layer only when building
custom infrastructure such as a backend or widget library without the reactive DSL.

## Can I use widgets without signals or TuiApp?

Yes. `tui-widgets` depends only on `tui-core`. Render a `Widget` or
`StatefulWidget[S]` directly into a `Buffer` and drive it with your own backend or
loop. The DSL wraps the same renderers; it is not a parallel implementation.

## Which terminals are supported?

Production rendering uses JLine 3 and ANSI capabilities. Modern Unix terminals and
Windows Terminal are the intended environments. Exact color, hyperlink, mouse,
focus-reporting, and emoji behavior depends on the emulator and installed fonts.
glyphora detects a missing controlling TTY and exits safely.

## Does it work on Windows?

The project compiles and tests on Windows in CI. Terminal behavior still depends on
the emulator and JLine support; Windows Terminal is the recommended environment.

## Can widgets be tested without image snapshots?

Yes. Widgets render into a `Buffer`; string helpers expose normalized lines and
text. `Pilot` covers full event/render loops and produces deterministic text screens
for ordinary ScalaTest assertions.

`Pilot` and `BufferAssertions` currently live in the repository's internal
`test-support` module rather than a published Maven artifact. `HeadlessBackend` is
part of the terminal module and can be driven directly downstream.

## Why are Signal writes restricted to the render thread?

A single writer makes rendering deterministic and avoids locks throughout widget
code. Background work can run anywhere; only its final UI state mutation hops back
to the render thread.

## How do I perform HTTP or disk work?

Use `Async.run` or `Async.runCatching`. Work runs on a daemon worker; its completion
is marshalled onto the render thread where it can safely update signals. For a
third-party callback, use `RenderThread.runOnRenderThread`. See
[Async work & timers](./async-and-timers).

## Does Unicode support include emoji and CJK?

Yes. Width calculation accounts for combining marks, East Asian wide characters,
variation selectors, flags, and emoji ZWJ sequences. Custom widgets must use
`CharWidth` rather than `String.length` for layout math.

## What accessibility support exists?

Every built-in interactive widget has a keyboard path, focus is explicit and
themeable, `Theme.HighContrast` ships with the DSL, and `Form.accessible` spells out
field position, checkbox state, and errors. Terminal screen-reader behavior varies,
so applications should avoid color-only status and keep command flows simple. See
[Unicode & accessibility](./unicode-and-accessibility).

## Is runtime reflection required?

No. Runtime reflection is forbidden by CI. Form and action derivation happens at
compile time through Scala 3 macros, keeping native-image builds free of reflection
configuration.

## Can I style every widget through the DSL?

Style-aware elements support fluent modifiers and semantic themes. Raw `widget(...)`
leaves and raster images own their rendering and intentionally ignore inherited
element styles. Use their constructor's style parameters at that level.

## Is glyphora stable?

The project is pre-1.0. Patch releases preserve APIs; minor releases may break them.
Core types are treated as the stability anchor, but applications should pin an exact
version and read release notes before a minor upgrade. See [Versioning](./versioning).

## Where is the complete API reference?

The [Scaladoc API](pathname:///api/) is generated for every published module and
bundled into the GitHub Pages deployment. This guide explains concepts and tasks;
Scaladoc is the source for exact signatures.
