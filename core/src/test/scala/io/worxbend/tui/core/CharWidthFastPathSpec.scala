package io.worxbend.tui.core

import org.scalacheck.Gen
import org.scalatest.funsuite.AnyFunSuite
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks

/** The printable-ASCII fast path in [[CharWidth]] exists purely for speed, so its only real requirement is that it can
  * never disagree with the general grapheme-walking path.
  */
final class CharWidthFastPathSpec extends AnyFunSuite, ScalaCheckPropertyChecks:

  /** The general path, with the fast path bypassed — what the fast path must reproduce. */
  private def generalWidth(text: String): Int =
    var total    = 0
    val clusters = CharWidth.graphemeClusters(text)
    while clusters.hasNext do
      val cluster = clusters.next()
      // one cluster at a time: a single cluster is never "printable ASCII" plus something else
      total += (if CharWidth.isPrintableAscii(cluster) then cluster.length else CharWidth.of(cluster))
    total

  test("printable ASCII is exactly one column per character"):
    forAll(Gen.stringOf(Gen.choose(0x20.toChar, 0x7e.toChar))) { text =>
      assert(CharWidth.of(text) == text.length)
    }

  test("the fast path agrees with the general path on mixed text"):
    val mixed = Gen.stringOf(
      Gen.oneOf(Gen.choose(0x20.toChar, 0x7e.toChar), Gen.const('漢'), Gen.const('é'), Gen.const('́'))
    )
    forAll(mixed) { text =>
      assert(CharWidth.of(text) == generalWidth(text))
    }

  test("control characters keep width zero and so are excluded from the fast path"):
    assert(!CharWidth.isPrintableAscii("a\tb"))
    assert(!CharWidth.isPrintableAscii("a" + 0.toChar + "b"))
    assert(!CharWidth.isPrintableAscii("ab"))
    assert(CharWidth.of("a\tb") == 2)

  test("substringByWidth truncates ASCII the same way as the general path"):
    forAll(Gen.stringOf(Gen.choose(0x20.toChar, 0x7e.toChar)), Gen.choose(0, 40)) { (text, width) =>
      val prefix = CharWidth.substringByWidth(text, width)
      assert(CharWidth.of(prefix) <= width)
      assert(text.startsWith(prefix))
      assert(prefix.length == math.min(text.length, math.max(0, width)))
    }

  test("substringByWidth never splits a wide cluster"):
    assert(CharWidth.substringByWidth("漢字", 3) == "漢")
    assert(CharWidth.substringByWidth("漢字", 4) == "漢字")
    assert(CharWidth.substringByWidth("漢字", 1) == "")

  test("asciiSymbol returns shared instances and nothing outside the printable range"):
    assert(CharWidth.asciiSymbol('a') eq CharWidth.asciiSymbol('a'))
    assert(CharWidth.asciiSymbol('a') == "a")
    assert(CharWidth.asciiSymbol(' ') == " ")
    assert(CharWidth.asciiSymbol('~') == "~")
    // `asciiSymbol` returns a null sentinel outside printable ASCII by design — its Scaladoc explains why an `Option`
    // was rejected there. This is the test that pins that contract, so it is the one place that has to name it.
    def isSentinel(symbol: String): Boolean = symbol == null // scalafix:ok DisableSyntax; the documented sentinel
    assert(isSentinel(CharWidth.asciiSymbol(27.toChar)))
    assert(isSentinel(CharWidth.asciiSymbol('漢')))
