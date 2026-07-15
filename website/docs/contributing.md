---
title: Contributing
---

# Contributing

## Developing glyphora

```bash
./mill __.compile                                   # build everything
./mill __.test                                      # ~1.5k tests, headless
./mill mill.scalalib.scalafmt.ScalafmtModule/reformatAll __.sources
```

The build is [Mill](https://mill-build.org); every module has its own `package.mill`
and a `test` submodule wired to ScalaTest (see `build.mill` for the shared `TuiModule`
/ `TuiPublishModule` conventions).

## House rules (CI-enforced)

- No `java.lang.reflect`/`Class.forName` anywhere outside `tui-macros`.
- No `String.length`/`substring` for layout math outside `CharWidth`.
- Warnings are errors (`-deprecation -feature -unchecked -Wunused:all -Werror`).
- Scalafmt owns formatting — CI runs `checkFormatAll`, it doesn't auto-format for you.

For general Scala style (naming, error handling, package layout) beyond these
repo-specific rules, see [`SCALA_CODE_STYLE.md`](https://github.com/oleksandr-balyshyn/glyphora/blob/main/SCALA_CODE_STYLE.md).

## Adding a widget

The checklist that keeps quality flat:

1. Implement against `Widget`/`StatefulWidget[S]` in `widgets/` (state is
   caller-owned; all width math through `CharWidth`).
2. Render-to-`Buffer` tests via `BufferAssertions.rendered`.
3. DSL factory in `dsl/Element.scala` + export in `dsl.scala` (focusable elements get
   a `builtinKeyHandler`, mouse behavior via `builtinMouseHandler`).
4. If interactive: an end-to-end `Pilot` test.

See [Widget catalog](./widgets) for where a new widget fits tier-wise, and
[Testing](./testing) for the test helpers.

## Pull requests

- Run `./mill __.compile && ./mill __.test` before opening a PR — CI runs the same
  gates plus the reflection/`CharWidth` discipline greps and a native-image build for
  every example.
- Keep changes scoped: a widget addition doesn't need to also refactor unrelated
  modules.
