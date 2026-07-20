---
title: Contributing
description: Set up glyphora locally, understand quality gates, add a widget, improve docs, and prepare a focused pull request.
---

# Contributing

Contributions are welcome across runtime behavior, widgets, examples, tests,
documentation, and design. The project favors small typed layers, visible ownership,
and tests that exercise the same buffers users see.

## Set up the repository

```bash
git clone git@github.com:oleksandr-balyshyn/glyphora.git
cd glyphora

./mill __.compile
./mill __.test
```

The build uses Mill. Every Scala module has a `package.mill`; shared Scala version,
strict compiler flags, ScalaTest wiring, and publication metadata live in
`build.mill`.

Useful development commands:

```bash
# Apply Scala formatting
./mill mill.scalalib.scalafmt.ScalafmtModule/reformatAll __.sources

# Verify formatting without changing files
./mill mill.scalalib.scalafmt.ScalafmtModule/checkFormatAll __.sources

# Run one module or example suite
./mill widgets.test
./mill examples.todo-list.test

# Manual terminal test bed
./mill examples.showcase.run

# Render-loop sanity check
./mill widgets.test.runMain io.worxbend.tui.widgets.RenderLoopBench
```

## Know the quality rules

CI enforces constraints that protect the design:

- no `java.lang.reflect` or `Class.forName` in main Scala sources;
- no `String.substring` for layout math outside `CharWidth`;
- warnings are errors (`-deprecation -feature -unchecked -Wunused:all -Werror`);
- Scalafmt owns formatting;
- all tests run headlessly on Linux, with best-effort Windows coverage;
- six example apps compile with GraalVM `--no-fallback` and start safely without a
  TTY.

For general Scala conventions, read
[`SCALA_CODE_STYLE.md`](https://github.com/oleksandr-balyshyn/glyphora/blob/main/SCALA_CODE_STYLE.md).

## Add a widget

1. **Choose state ownership.** A stateless widget implements `Widget`; an interactive
   or scrollable renderer uses `StatefulWidget[S]`, with state owned by the caller.
2. **Implement in `widgets/`.** Depend only on `tui-core`. Render inside the supplied
   `Rect`, clip safely, and route all visible-width logic through `CharWidth`.
3. **Test the buffer.** Cover empty and tiny areas, normal content, truncation,
   Unicode, selection/focus style, and state boundaries with `BufferAssertions`.
4. **Add the DSL element.** Put its retained data and built-in key/mouse behavior in
   `dsl/Element.scala`; add a factory in `object Element` and export it from
   `dsl.scala`.
5. **Test interaction.** Use `Pilot` for focus, keys, mouse behavior, resize, and
   redraw when the widget is interactive.
6. **Document it.** Add it to [Widget catalog](./widgets), provide a short realistic
   snippet, and use it in an example when it introduces a new pattern.

Keep user handlers ahead of built-in behavior and return `false` when an event should
continue bubbling.

## Add or change a public API

- Put the API in the lowest layer that can own it without creating an upward
  dependency.
- Prefer data types, sealed ADTs, and direct calls over runtime discovery.
- Write Scaladoc that explains ownership, thread constraints, and edge behavior—not
  only parameter names.
- Add tests at the owning layer and an integration test when multiple layers must
  cooperate.
- Update the task guide and API usage examples in the same pull request.

## Improve the documentation

Canonical guide Markdown lives in `website/docs/`. Docusaurus publishes it to
GitHub Pages and `scripts/export-wiki.mjs` turns the same pages/navigation into the
GitHub Wiki.

```bash
cd website
npm ci
npm run build

cd ..
node scripts/export-wiki.mjs --output build/wiki
```

Follow [`docs/STYLE_GUIDE.md`](https://github.com/oleksandr-balyshyn/glyphora/blob/main/docs/STYLE_GUIDE.md):
lead with the reader's outcome, explain why, include a verified snippet, name the
terminal-specific pitfalls, and point to the next useful guide. Assets shared by the
README, Pages, and Wiki belong in `docs/assets/`.

When adding a page, add its ID to `website/docs-navigation.mjs`; that one navigation
tree drives both Docusaurus and the generated Wiki sidebar.

## Prepare a pull request

- Keep the change focused and explain the user-visible outcome first.
- Include commands you ran and any terminal/emulator used for manual verification.
- Add screenshots only when the change is genuinely visual; prefer text/buffer
  assertions for behavior.
- Call out public API and native-image implications explicitly.
- Do not commit generated `website/build` or `build/wiki` output.

Before pushing:

```bash
./mill mill.scalalib.scalafmt.ScalafmtModule/checkFormatAll __.sources
./mill __.compile
./mill __.test
(cd website && npm run build)
node scripts/export-wiki.mjs --output build/wiki
```
