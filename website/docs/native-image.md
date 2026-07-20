---
title: Native binaries
description: Compile glyphora applications into self-contained GraalVM executables with no fallback image and no reflection configuration.
---

# Ship a native binary

glyphora treats GraalVM compatibility as an architecture rule, not a late packaging
experiment. The runtime does not scan classes or use reflection; Scala 3 macros
generate direct form and action wiring during compilation.

The result is a self-contained terminal executable built with `--no-fallback` and no
`reflect-config.json`.

## Build an example

```bash
./mill examples.showcase.nativeImage
```

The example module declares:

```scala title="examples/showcase/package.mill"
object `package` extends build.TuiModule with NativeImageModule:
  def moduleDeps = Seq(
    build.core,
    build.terminal,
    build.runtime,
    build.widgets,
    build.dsl,
  )

  def mainClass = Some("io.worxbend.tui.examples.showcase.Main")
  def jvmVersion = "graalvm-community:23.0.1"
  def nativeImageOptions = Seq("--no-fallback")
```

Mill resolves the configured GraalVM toolchain and writes the executable under the
module's `out/.../nativeImage.dest/` directory.

## Add native-image to your module

```scala title="build.mill"
import mill.*, scalalib.*, javalib.NativeImageModule

object app extends ScalaModule with NativeImageModule:
  def scalaVersion = "3.7.1"
  def mvnDeps = Seq(mvn"io.worxbend::tui-dsl:0.10.0")
  def mainClass = Some("example.Main")
  def jvmVersion = "graalvm-community:23.0.1"
  def nativeImageOptions = Seq("--no-fallback")
```

Then build:

```bash
./mill app.nativeImage
```

Keep `--no-fallback`: without it, native-image may silently produce a launcher that
still needs a JVM, hiding compatibility problems until release.

## Why zero reflection matters

Closed-world compilation must know every reachable class and method. Reflection and
dynamic class loading conceal that graph, which usually leads to hand-maintained
configuration files that drift as code changes.

glyphora avoids the problem:

- `deriveForm[A]` uses Scala 3 `Mirror` at compile time and emits direct calls;
- event, widget, and state types are ordinary sealed/data structures;
- service discovery and runtime classpath scanning are absent;
- CI rejects `java.lang.reflect` and `Class.forName` in main Scala sources.

This rule also improves JVM behavior: fewer hidden code paths, clearer dependencies,
and errors that appear during compilation instead of at runtime.

## Test the executable without a TTY

CI builds native executables for `hello-world`, `counter`, `todo-list`, `dashboard`,
`form-demo`, and `showcase`. It then launches each in a headless job and verifies a
clean `UnsupportedTerminal` response instead of a hang or corrupt raw-mode setup.

```bash
./out/examples/showcase/nativeImage.dest/native-executable
# no TTY: exits with an UnsupportedTerminal message
```

For behavior tests, keep using `HeadlessBackend` on the JVM. Native CI is a packaging
and reachability gate; the headless suite provides fast interaction coverage.

## Diagnose build failures

1. Confirm the JVM build and tests first:

   ```bash
   ./mill app.compile
   ./mill app.test
   ```

2. Build the smallest failing application with `--no-fallback` still enabled.
3. Inspect your dependencies for runtime reflection, JNI, dynamic proxies, resource
   lookup, or classpath scanning.
4. Prefer direct registration or compile-time derivation over adding broad native
   configuration.
5. If a third-party library truly needs GraalVM metadata, keep that metadata beside
   the application and test it in CI.

## Release checklist

- build on the same OS/architecture you distribute;
- run `--help` and a headless startup smoke test;
- run the binary in a real terminal and verify resize, mouse, paste, and cleanup;
- compare any filesystem/resource lookup with the JVM version;
- preserve license notices for bundled dependencies;
- publish a checksum with downloadable artifacts.

Read [Architecture](./architecture#tui-macros) for the compile-time layer and
[Troubleshooting](./troubleshooting#native-image-compilation-fails) for failure
triage.
