---
title: Versioning
---

# Versioning

Pre-1.0: minor versions (`0.x`) may break APIs, patches never do. `tui-core` is the
stability anchor — additive changes only since `0.2`.

Releases are git tags (`vX.Y.Z`); pushing a tag publishes all `tui-*` modules to Maven
Central via the `Publish` GitHub Actions workflow. Binary-compatibility gates via MiMa
arrive with the first Central release as baseline.

## Where to get it

```scala
// Mill
def mvnDeps = Seq(mvn"io.worxbend::tui-dsl:0.10.0")
```

```scala
// sbt
libraryDependencies += "io.worxbend" %% "tui-dsl" % "0.10.0"
```

Check [Maven Central](https://search.maven.org/search?q=g:io.worxbend) or the
[release tags](https://github.com/oleksandr-balyshyn/glyphora/tags) for the latest
version.

## License

[MIT](https://github.com/oleksandr-balyshyn/glyphora/blob/main/LICENSE) — go build
something glyphorious.
