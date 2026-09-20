package io.worxbend.tui.core

import org.scalacheck.Gen
import org.scalatest.funsuite.AnyFunSuite
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks

/** Property-based invariants for [[CharWidth]] — the laws every width answer has to satisfy, whatever the text.
  *
  * The alphabet matters more here than the number of cases. A generator of letters, a CJK ideograph and an emoji never
  * produces a variation selector, a lone zero-width joiner, a regional indicator on its own or a control character, so
  * it never reaches the branches of `clusterWidth` whose hand-written regressions sit next to it in [[CharWidthSpec]].
  * Every piece below is here because it takes a different path through that method.
  */
final class CharWidthPropertySpec extends AnyFunSuite with ScalaCheckPropertyChecks:

  // ScalaTest's default is ten cases, which is not a sample of a space this shape
  implicit override val generatorDrivenConfig: PropertyCheckConfiguration =
    PropertyCheckConfiguration(minSuccessful = 300)

  private def fromCodePoint(value: Int): String = Character.toChars(value).mkString

  /** Spelled by codepoint rather than as literals: most of these are invisible in an editor. */
  private val ZeroWidthJoiner: String           = fromCodePoint(0x200d)
  private val TextPresentationSelector: String  = fromCodePoint(0xfe0e)
  private val EmojiPresentationSelector: String = fromCodePoint(0xfe0f)
  private val CombiningAcute: String            = fromCodePoint(0x0301)
  private val UnitSeparator: String             = fromCodePoint(0x001f) // a C0 control: one code unit, zero columns
  private val RegionalIndicatorU: String        = fromCodePoint(0x1f1fa)
  private val RegionalIndicatorA: String        = fromCodePoint(0x1f1e6)
  private val ThumbsUp: String                  = fromCodePoint(0x1f44d)
  private val HeavyBlackHeart: String           = fromCodePoint(0x2764) // a legacy emoji base outside the emoji planes

  /** The pieces text is assembled from. The two regional indicators are deliberately separate, so the generator can
    * build a flag, half a flag, or three in a row — the pairing rule is the one place a cluster is closed early.
    */
  private val interestingPieces: Gen[String] = Gen.oneOf(
    Gen.alphaNumChar.map(_.toString),
    Gen.const(" "),
    Gen.const("你"),
    Gen.const("é"),
    Gen.const(ThumbsUp),
    Gen.const(HeavyBlackHeart),
    Gen.const(RegionalIndicatorU),
    Gen.const(RegionalIndicatorA),
    Gen.const(CombiningAcute),
    Gen.const(ZeroWidthJoiner),
    Gen.const(EmojiPresentationSelector),
    Gen.const(TextPresentationSelector),
    Gen.const(UnitSeparator),
  )

  /** Truncated by *piece*, never by code unit: `String.take` would split a surrogate pair and the property would then
    * be about lone surrogates rather than about the text anyone writes.
    */
  private val genText: Gen[String] = Gen.listOf(interestingPieces).map(_.take(20).mkString)

  test("width is never negative and empty text is zero"):
    forAll(genText) { text =>
      assert(CharWidth.of(text) >= 0)
    }
    assert(CharWidth.of("") == 0)

  test("no grapheme cluster is wider than two columns"):
    // A Buffer cell reserves exactly one continuation column for a wide cluster. A cluster that measured three would
    // be written into a cell whose neighbour was never reserved, and every column after it in that row would shift.
    forAll(genText) { text =>
      val clusters = CharWidth.graphemeClusters(text).toSeq
      clusters.foreach { cluster =>
        val width = CharWidth.of(cluster)
        assert(width >= 0 && width <= 2, s"cluster [$cluster] measured $width columns")
      }
      assert(CharWidth.of(text) <= 2 * clusters.size)
    }

  test("clusterCount agrees with walking the clusters"):
    // clusterCount carries its own copy of the printable-ASCII shortcut; this is what keeps the copy honest.
    forAll(genText) { text =>
      assert(CharWidth.clusterCount(text) == CharWidth.graphemeClusters(text).size)
    }

  test("substringByWidth never exceeds its budget and is a prefix"):
    forAll(genText, Gen.chooseNum(0, 30)) { (text, budget) =>
      val prefix = CharWidth.substringByWidth(text, budget)
      assert(CharWidth.of(prefix) <= math.max(0, budget))
      assert(text.startsWith(prefix))
    }

  test("a larger budget never yields a shorter prefix"):
    // Truncation is what every widget does to text that does not fit, so it has to be monotone in the space available:
    // widening a column must never drop a character that the narrower column kept.
    forAll(genText, Gen.chooseNum(0, 30), Gen.chooseNum(0, 30)) { (text, first, second) =>
      val narrow = CharWidth.substringByWidth(text, math.min(first, second))
      val wide   = CharWidth.substringByWidth(text, math.max(first, second))
      assert(wide.startsWith(narrow), s"[$text]: $first gave [$narrow], $second gave [$wide]")
    }

  test("dropByWidth forfeits at most one column beyond the one it was asked to skip"):
    // The guarantee dropByWidth's Scaladoc makes to a horizontally scrolled view: a cluster straddling the boundary is
    // dropped whole, so the suffix can be one column shorter than the arithmetic suggests — but never two.
    forAll(genText, Gen.chooseNum(0, 30)) { (text, skip) =>
      val suffix = CharWidth.dropByWidth(text, skip)
      assert(text.endsWith(suffix))
      assert(CharWidth.of(suffix) <= CharWidth.of(text))
      assert(
        CharWidth.of(suffix) >= CharWidth.of(text) - skip - 1,
        s"[$text] skipping $skip left [$suffix], which is more than one column short",
      )
    }

  test("joining two strings changes the width only of the cluster at the seam"):
    // Deliberately *not* stated as sub-additivity, which is false: joining closes a cluster across the boundary that
    // neither side could form alone, and that cluster is worth up to two columns where its parts measured none. Both
    // mechanisms are named in the test below. What survives is the bound: only the seam is re-segmented, so the gain
    // can never exceed one cluster's width, and a cluster is at most two columns. An implementation that let a join
    // grow the text by an unbounded amount — the failure mode a caller laying a row out as a run of segments pays for
    // — breaks this, while the two real counterexamples do not.
    forAll(genText, genText) { (first, second) =>
      val joined = CharWidth.of(first + second)
      val apart  = CharWidth.of(first) + CharWidth.of(second)
      assert(joined <= apart + 2, s"[$first] + [$second] measured $joined joined but $apart apart")
    }

  test("the two ways a concatenation comes out wider than its parts"):
    // Why the property above allows a gain at all, rather than a comment saying it does.
    //
    // One: U+FE0F after a keycap base asks for the emoji presentation, which is two columns; measured apart the digit
    // is one column and the selector is zero. U+FE0E asks for the text presentation and so can only ever narrow.
    assert(CharWidth.of("1") == 1)
    assert(CharWidth.of(EmojiPresentationSelector) == 0)
    assert(CharWidth.of("1" + EmojiPresentationSelector) == 2)
    assert(CharWidth.of("1" + TextPresentationSelector) == 1)
    // and a base with no emoji presentation is unmoved by either, which keeps that effect to the keycap case
    assert(CharWidth.of("a" + EmojiPresentationSelector) == 1)
    // Two, and not a selector at all: regional indicators pair up. A lone one is a single column, and a second one
    // arriving from the other side of the seam completes a flag worth two — which frees the joiner behind it to start
    // a cluster of its own instead of being absorbed. This is the counterexample the broadened alphabet found.
    assert(CharWidth.of(RegionalIndicatorU) == 1)
    assert(CharWidth.of(RegionalIndicatorU + ZeroWidthJoiner + "6") == 1)
    assert(CharWidth.of(RegionalIndicatorU + RegionalIndicatorU + ZeroWidthJoiner + "6") == 3)

  test("cluster widths partition the total width"):
    forAll(genText) { text =>
      val clusters = CharWidth.graphemeClusters(text).toSeq
      assert(clusters.map(CharWidth.of).sum == CharWidth.of(text))
      assert(clusters.mkString == text)
    }
