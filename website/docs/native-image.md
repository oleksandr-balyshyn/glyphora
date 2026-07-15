---
title: Native binaries
---

# Native binaries

```bash
./mill show examples.showcase.nativeImage   # → a self-contained executable
```

Every example builds with GraalVM `--no-fallback` and **no reflect-config JSON** — the
framework bridges user code with Scala 3 `inline`/`Mirror` instead of reflection (see
[`tui-macros`](./architecture#tui-macros)).

CI builds native images for every example (`examples.hello-world`, `counter`,
`todo-list`, `dashboard`, `form-demo`, `showcase`) on GraalVM community 23.0.1, then
runs each binary headlessly to confirm it detects the missing TTY and exits cleanly
rather than hanging or crashing.

## Why this matters

Reflection-based frameworks typically need hand-maintained `reflect-config.json` to
work under `native-image` — a maintenance burden that silently breaks when new
reflective call sites are added. glyphora's house rule (CI-enforced: a grep over all
main sources) is that no code outside `tui-macros`' compile-time codegen may use
`java.lang.reflect` or `Class.forName`. Everywhere the framework needs to bridge
user-defined code — `deriveForm`, `bindAction` — it does so with Scala 3 macros that
run at compile time and generate direct calls, so there is nothing for `native-image`
to fail to see.

The payoff: native binaries start in milliseconds and need zero extra native-image
configuration beyond `--no-fallback`.
