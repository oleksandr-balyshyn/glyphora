package io.worxbend.tui.core

import org.scalatest.funsuite.AnyFunSuite

/** Covers the `hex"…"` compile-time colour literal.
  *
  * Half of what this interpolator promises cannot be observed at run time: a malformed literal is supposed to stop the
  * build. `scala.compiletime.testing.typeChecks` is how that half is tested — it reports whether a snippet compiles
  * without actually admitting it into the program, so a rejection can be asserted rather than only demonstrated by a
  * commented-out line.
  */
final class ColorLiteralSpec extends AnyFunSuite:

  test("a six-digit literal expands to the colour it names"):
    assert(hex"#ff8800" == Color.Rgb(255, 136, 0))
    assert(hex"c83232" == Color.Rgb(200, 50, 50)) // the leading # is optional, as in Color.hex

  test("a three-digit literal expands each nibble to a byte, as Color.hex does"):
    assert(hex"#f80" == Color.Rgb(255, 136, 0))
    assert(hex"f80" == Color.hex("#f80").get)

  test("the literal is case-insensitive"):
    assert(hex"#FF8800" == hex"#ff8800")
    assert(hex"#AbCdEf" == Color.Rgb(0xab, 0xcd, 0xef))

  test("the extremes come out exact"):
    assert(hex"#000000" == Color.Rgb(0, 0, 0))
    assert(hex"#ffffff" == Color.Rgb(255, 255, 255))

  test("the interpolator and Color.hex agree on every literal they both accept"):
    // one parser, used at compile time here and at run time there, so the two cannot drift apart
    assert(hex"#123456" == Color.hex("#123456").get)
    assert(hex"#abc" == Color.hex("#abc").get)

  test("a malformed literal is a compile error, not a runtime None"):
    assert(
      !scala.compiletime.testing.typeChecks("""import io.worxbend.tui.core.hex; hex"#ff88"""")
    ) // four digits: neither 3 nor 6
    assert(
      !scala.compiletime.testing.typeChecks("""import io.worxbend.tui.core.hex; hex"#12345g"""")
    ) // g is not a hex digit
    assert(!scala.compiletime.testing.typeChecks("""import io.worxbend.tui.core.hex; hex"nothex""""))
    assert(!scala.compiletime.testing.typeChecks("""import io.worxbend.tui.core.hex; hex""""")) // empty

  test("an interpolated value is rejected, because it cannot be checked at compile time"):
    assert(!scala.compiletime.testing.typeChecks("""import io.worxbend.tui.core.hex; val v = "ff8800"; hex"#$v""""))

  test("a valid literal does compile, so the rejections above are not vacuous"):
    // without this, a typo in the snippets would make every typeChecks assertion pass for the wrong reason
    assert(scala.compiletime.testing.typeChecks("""import io.worxbend.tui.core.hex; hex"#ff8800""""))
