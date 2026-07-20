---
title: Versioning & releases
description: Understand glyphora's pre-1.0 compatibility policy, synchronized module versions, Maven coordinates, and release process.
---

# Versioning & releases

glyphora is pre-1.0. Patch releases preserve public APIs; minor releases may make
breaking changes as the application layer matures. `tui-core` is the stability
anchor and has received additive changes only since `0.2`.

Pin an exact version in applications and review release notes before moving between
minor versions.

## Current coordinates

All published modules share one synchronized version under `io.worxbend`:

```scala
// Mill
def mvnDeps = Seq(mvn"io.worxbend::tui-dsl:0.10.0")
```

```scala
// sbt
libraryDependencies += "io.worxbend" %% "tui-dsl" % "0.10.0"
```

Applications normally need only `tui-dsl`. Lower-tier artifacts are `tui-core`,
`tui-terminal`, `tui-widgets`, `tui-runtime`, and `tui-macros`.

Check [Maven Central](https://search.maven.org/search?q=g:io.worxbend) and
[release tags](https://github.com/oleksandr-balyshyn/glyphora/tags) before choosing a
version.

## Compatibility policy

| Change | Patch release | Minor release before 1.0 |
|---|---:|---:|
| bug fix preserving signatures | yes | yes |
| additive widget or method | yes, when low risk | yes |
| source-breaking rename/removal | no | possible, documented |
| behavior change with migration work | no | possible, documented |
| binary compatibility guarantee | not yet | not yet |

MiMa binary-compatibility gates are planned once a first published baseline is
selected. Until then, recompile downstream code on upgrade even when moving between
patch versions.

## Release process

Releases are Git tags named `vX.Y.Z`. Pushing a tag runs the Publish workflow, which
publishes every `TuiPublishModule` to Maven Central with the same version and signed
POM metadata.

Before a tag, maintainers should verify:

- formatter, compile, and complete test suite;
- public guide and Scaladoc changes;
- example JVM runs and native-image CI;
- module version in `build.mill`;
- migration notes for any source or behavior change.

## License

[MIT](https://github.com/oleksandr-balyshyn/glyphora/blob/main/LICENSE) — use glyphora
in open-source or commercial software, keeping the copyright and license notice.
