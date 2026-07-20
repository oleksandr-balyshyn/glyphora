---
title: FAQ
---

# Frequently asked questions

## Is glyphora a Scala port of another TUI framework?

No. It borrows proven ideas—immutable descriptions, buffer rendering, reactive
state, and backend isolation—but its API is designed around Scala 3 capabilities,
compile-time derivation, and GraalVM constraints.

## Which module should an application depend on?

Use `tui-dsl` for normal applications. It transitively includes core, widgets, and
runtime. Depend on a lower layer only when building custom infrastructure, such as a
new backend or a widget library without the reactive DSL.

## Does it work on Windows?

The project compiles and tests on Windows in CI. Terminal behavior still depends on
the emulator and JLine support; Windows Terminal is the recommended environment.

## Can widgets be tested without snapshot images?

Yes. Widgets render into a `Buffer`, and `BufferAssertions` exposes normalized lines
and text. `Pilot` covers full event/render loops and produces deterministic text
screens suitable for ordinary ScalaTest assertions.

## Why are `Signal` writes restricted to the render thread?

A single writer makes rendering deterministic and avoids locks throughout widget
code. Background work can still run anywhere; only the final state mutation hops
back to the render thread.

## Does Unicode support include emoji and CJK?

Yes. Width calculation accounts for combining marks, East Asian wide characters,
variation selectors, flags, and emoji ZWJ sequences. Custom widgets must use
`CharWidth` rather than `String.length` for layout math.

## Is runtime reflection required?

No. Runtime reflection is forbidden by CI. Form and action derivation happens at
compile time through Scala 3 macros, keeping native-image builds free of reflection
configuration.

## Where is the complete API reference?

The [Scaladoc API](pathname:///api/) is generated for each published module and
bundled into the GitHub Pages deployment. The guide explains concepts and workflows;
Scaladoc is the source for exact signatures.
