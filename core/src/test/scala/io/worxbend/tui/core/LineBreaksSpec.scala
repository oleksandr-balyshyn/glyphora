package io.worxbend.tui.core

import org.scalatest.funsuite.AnyFunSuite

final class LineBreaksSpec extends AnyFunSuite:

  test("ordinary blanks are break opportunities"):
    assert(LineBreaks.isBreakingSpace(" "))
    assert(LineBreaks.isBreakingSpace("\t"))
    assert(LineBreaks.isBreakingSpace("　")) // ideographic space

  test("the three no-break spaces are never break opportunities"):
    // These are the assumptions LineBreaks leans on java.lang.Character for; pinning them here means a JDK whose
    // answer ever changed would fail this test rather than silently start breaking "10 kg" in half.
    assert(!LineBreaks.isBreakingSpace(LineBreaks.NoBreakSpace.toString))
    assert(!LineBreaks.isBreakingSpace(" ")) // figure space
    assert(!LineBreaks.isBreakingSpace(" ")) // narrow no-break space

  test("zero width space is a break opportunity but not a discardable blank"):
    val zwsp = LineBreaks.ZeroWidthSpace.toString
    assert(LineBreaks.isZeroWidthBreak(zwsp))
    assert(!LineBreaks.isBreakingSpace(zwsp))
    assert(CharWidth.of(zwsp) == 0)

  test("a zero width space is found at the end of the cluster it was absorbed into"):
    // A zero-width character joins the cluster in front of it, so this is the shape a wrapper really meets.
    val absorbed = CharWidth.graphemeClusters("a" + LineBreaks.ZeroWidthSpace + "b").toSeq
    assert(absorbed.head == "a" + LineBreaks.ZeroWidthSpace)
    assert(LineBreaks.endsWithZeroWidthBreak(absorbed.head))
    assert(!LineBreaks.endsWithZeroWidthBreak("ab"))
    assert(!LineBreaks.endsWithZeroWidthBreak(""))

  test("printable text is neither"):
    Seq("a", "你", "🙂", "é", "🇩🇪").foreach { cluster =>
      assert(!LineBreaks.isBreakingSpace(cluster), cluster)
      assert(!LineBreaks.isZeroWidthBreak(cluster), cluster)
    }

  test("a multi-code-point cluster is never a break opportunity even when it starts with a blank"):
    // A space carrying a combining mark is one user-perceived character; breaking on it would split the cluster.
    assert(!LineBreaks.isBreakingSpace(" ́"))
    assert(!LineBreaks.isZeroWidthBreak(LineBreaks.ZeroWidthSpace.toString + "a"))

  test("the empty string is not a break opportunity"):
    assert(!LineBreaks.isBreakingSpace(""))
    assert(!LineBreaks.isZeroWidthBreak(""))
