<!--
Thanks for contributing. Fill in the four sections below; delete anything that does not
apply. The full contributor guide is in CONTRIBUTING.md.
-->

## What this does

<!-- Plain language, for a reader who has never seen this project. -->

## Why

<!-- The bug, the missing capability, or the user-facing pain. Link the issue: Fixes #123 -->

## How it works

<!--
Name the files and functions you touched and say what each one does now. Explain any
non-obvious decision, and what you rejected. If this changes public API, say so here and
show the before/after — the library is pre-1.0, so breaking changes are allowed, but
they have to be visible.
-->

## How to test it

<!-- The exact commands, the expected output, and how to reproduce the original problem. -->

---

## Checks

These are the six gates that actually fail the build. CI runs all of them; running them
locally first is faster than a red run.

- [ ] **No runtime reflection in main sources** — no `java.lang.reflect`, no
      `Class.forName`. Compile-time derivation in `tui-macros` is the supported bridge to
      user code, and this is what keeps native images reflect-config-free.
- [ ] **No `String.substring` in main sources** outside `core/CharWidth.scala` — every
      display-width, truncation, and layout calculation goes through `CharWidth`.
- [ ] **Formatting** — `./mill mill.scalalib.scalafmt.ScalafmtModule/checkFormatAll __.sources`
- [ ] **Lints** — `./mill __.fix --check` (main *and* test sources; a real exception carries
      an inline `// scalafix:ok DisableSyntax; <why>`)
- [ ] **Warnings are errors** — `./mill __.compile` is clean under `-Wunused:all -Werror`
- [ ] **Every example still builds as a GraalVM native image** with `--no-fallback` and exits
      cleanly with no TTY (CI does this for all ten; run
      `./mill examples.<name>.nativeImage` locally if you touched one)

And the ordinary ones:

- [ ] Tests run per module, not as `./mill __.test` — `./mill core.test`, `./mill widgets.test`, …
- [ ] New or changed behaviour has a test that would fail without the change
- [ ] Scaladoc updated for anything whose meaning changed, stating ownership and thread
      constraints where they apply
- [ ] Docs updated: `website/docs/`, and `website/docs-navigation.mjs` if you added a page
- [ ] No generated `website/build` or `build/wiki` output committed

## Notes for reviewers

<!-- Anything surprising, any known limitation, any follow-up deliberately left out of scope. -->
