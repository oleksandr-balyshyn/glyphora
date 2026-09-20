package io.worxbend.tui.core

import org.scalacheck.Gen
import org.scalatest.funsuite.AnyFunSuite
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks

/** What [[Rect]]'s arithmetic does at the ends of the `Int` range.
  *
  * A `Rect` is plain arithmetic over caller-supplied numbers, so every one of its methods is one wrapped multiplication
  * away from answering the opposite of the truth. [[Rect.inset]] was exactly that: `2 * margin` wrapped negative for a
  * margin near `Int.MaxValue`, so `width - 2 * margin` came out *larger* than `width` and a margin that should have
  * exhausted the rectangle handed back a wider one — a widget then drew outside the area it was given. That
  * multiplication is now done in `Long` and clamped, and these properties are what keeps it that way.
  *
  * The properties are deliberately split by domain. "The result lies inside the original" can only be stated about a
  * rectangle whose own far edge is representable, because [[Rect.right]] is plain `x + width` and wraps for a rectangle
  * placed at the top of the `Int` range — the last test here says that out loud rather than leaving the reader to
  * wonder why the generators are bounded.
  */
final class RectOverflowSpec extends AnyFunSuite, ScalaCheckPropertyChecks:

  // ten cases (ScalaTest's default) cannot find an edge case that lives at one specific power of two
  implicit override val generatorDrivenConfig: PropertyCheckConfiguration =
    PropertyCheckConfiguration(minSuccessful = 400)

  /** Coordinates and extents that straddle the points where 32-bit arithmetic stops being faithful. */
  private val extremes: Seq[Int] =
    Seq(0, 1, -1, Int.MaxValue, Int.MaxValue - 1, Int.MinValue, Int.MinValue + 1, 1 << 30, (1 << 30) + 1, -(1 << 30))

  private val genAnyInt: Gen[Int] = Gen.frequency(3 -> Gen.choose(-200, 200), 2 -> Gen.oneOf(extremes))

  /** A rectangle with no bounds at all on its fields — including ones whose own `right`/`bottom` already wrap. */
  private val genAnyRect: Gen[Rect] =
    for
      x <- genAnyInt
      y <- genAnyInt
      w <- genAnyInt
      h <- genAnyInt
    yield Rect(x, y, w, h)

  /** Half the `Int` range, so `x + width` and `union`'s `right - left` both stay representable. */
  private val Bound: Int = 1 << 29

  /** Terminal-sized most of the time, half the address space the rest of it. */
  private val genRepresentableExtent: Gen[Int] = Gen.frequency(1 -> Gen.choose(-5, 400), 1 -> Gen.choose(0, Bound))

  private val genRepresentableRect: Gen[Rect] =
    for
      x <- Gen.choose(-Bound, Bound)
      y <- Gen.choose(-Bound, Bound)
      w <- genRepresentableExtent
      h <- genRepresentableExtent
    yield Rect(x, y, w, h)

  /** Small enough to walk cell by cell. */
  private val genSmallRect: Gen[Rect] =
    for
      x <- Gen.choose(-8, 8)
      y <- Gen.choose(-8, 8)
      w <- Gen.choose(-2, 12)
      h <- Gen.choose(-2, 12)
    yield Rect(x, y, w, h)

  /** Margins a caller could plausibly pass, weighted towards the ones that used to wrap `2 * margin` negative. */
  private val genMargin: Gen[Int] =
    Gen.frequency(
      2 -> Gen.choose(0, 300),
      3 -> Gen.oneOf(Int.MaxValue, Int.MaxValue - 1, 1 << 30, (1 << 30) + 1, 1 << 29, 1 << 28),
    )

  private def corners(rect: Rect): Seq[Position] =
    if rect.isEmpty then Seq.empty
    else
      Seq(
        Position(rect.x, rect.y),
        Position(rect.right - 1, rect.y),
        Position(rect.x, rect.bottom - 1),
        Position(rect.right - 1, rect.bottom - 1),
      )

  test("a non-negative margin never grows either axis, whatever the rect"):
    forAll(genAnyRect, genMargin, genMargin) { (rect, horizontal, vertical) =>
      val inner = rect.inset(horizontal, vertical)
      assert(inner.width <= math.max(0, rect.width), s"$rect inset by ($horizontal, $vertical) widened to $inner")
      assert(inner.height <= math.max(0, rect.height), s"$rect inset by ($horizontal, $vertical) grew to $inner")
    }

  test("a larger margin never leaves a larger rect"):
    forAll(genAnyRect, genMargin, genMargin) { (rect, first, second) =>
      val smaller = math.min(first, second)
      val larger  = math.max(first, second)
      assert(
        rect.inset(larger).width <= rect.inset(smaller).width,
        s"$rect: margin $larger left more width than margin $smaller",
      )
      assert(
        rect.inset(larger).height <= rect.inset(smaller).height,
        s"$rect: margin $larger left more height than margin $smaller",
      )
    }

  test("an inset rect is empty or lies entirely inside the one it came from"):
    forAll(genRepresentableRect, genMargin, genMargin) { (rect, horizontal, vertical) =>
      val inner = rect.inset(horizontal, vertical)
      assert(inner.right >= inner.x, s"$inner wrapped its right edge below its origin")
      assert(inner.bottom >= inner.y, s"$inner wrapped its bottom edge above its origin")
      if !inner.isEmpty then
        assert(inner.x >= rect.x && inner.y >= rect.y, s"$inner starts outside $rect")
        assert(inner.right <= rect.right && inner.bottom <= rect.bottom, s"$inner ends outside $rect")
    }

  test("every cell of an inset rect is a cell of the original"):
    forAll(genSmallRect, Gen.choose(0, 6), Gen.choose(0, 6)) { (rect, horizontal, vertical) =>
      rect.inset(horizontal, vertical).positions.foreach { cell =>
        assert(rect.contains(cell), s"$rect inset by ($horizontal, $vertical) kept $cell, which was never inside it")
      }
    }

  test("union covers every cell of both inputs"):
    forAll(genSmallRect, genSmallRect) { (first, second) =>
      val merged = first.union(second)
      first.positions.foreach(cell => assert(merged.contains(cell), s"$merged lost $cell from $first"))
      second.positions.foreach(cell => assert(merged.contains(cell), s"$merged lost $cell from $second"))
    }

  test("union keeps both inputs' corners at coordinates far from the origin"):
    forAll(genRepresentableRect, genRepresentableRect) { (first, second) =>
      val merged = first.union(second)
      assert(merged.width >= 0 && merged.height >= 0, s"$first union $second wrapped to $merged")
      (corners(first) ++ corners(second)).foreach(cell =>
        assert(merged.contains(cell), s"$merged lost $cell from $first union $second")
      )
    }

  test("a margin near Int.MaxValue collapses the rect instead of growing it"):
    // The regression this file was written for. In 32-bit arithmetic `2 * Int.MaxValue` is -2, so `100 - (2 * margin)`
    // was 102: the biggest margin expressible handed back a rect two columns *wider* than the one it was given, placed
    // past the far end of the coordinate space.
    val area = Rect(4, 6, 100, 40)
    Seq(Int.MaxValue, Int.MaxValue - 1, 1 << 30, (1 << 30) + 1, 1 << 29).foreach { margin =>
      val inner = area.inset(margin)
      assert(inner.isEmpty, s"margin $margin left $area as $inner")
      assert(inner == Rect(54, 26, 0, 0), s"margin $margin collapsed $area somewhere other than its centre: $inner")
    }

  test("Int.MinValue as a margin outsets to the widest representable rect rather than leaving the extent untouched"):
    // `2 * Int.MinValue` is 0 in 32-bit arithmetic, so the extent used to come back unchanged. In `Long` it is -2^32,
    // which no `Int` extent can hold, so the result saturates at Int.MaxValue instead of wrapping negative.
    val inner = Rect(4, 6, 100, 40).inset(Int.MinValue)
    assert(inner.x == Int.MinValue + 4 && inner.y == Int.MinValue + 6, s"origin moved to (${inner.x}, ${inner.y})")
    assert(inner.width == Int.MaxValue && inner.height == Int.MaxValue, s"extent came out ${inner.size}")
    assert(!inner.isEmpty)

  test("a negative margin outsets an already maximal rect without overflowing its extent"):
    val outset = Rect(0, 0, Int.MaxValue, Int.MaxValue).inset(-1, -1)
    assert(outset.width == Int.MaxValue && outset.height == Int.MaxValue, s"extent came out ${outset.size}")
    assert(!outset.isEmpty)

  test("right and bottom wrap for a rect whose far edge is not representable"):
    // `right` is plain `x + width`, so a rect placed at the top of the `Int` range reports an edge to the *left* of its
    // own origin and `contains` then answers false for its own top-left cell. That is why the containment properties
    // above are stated over rects whose far edge still fits in an `Int`; this test is where that line is drawn, and it
    // fails the day `right` is changed to saturate instead.
    val unrepresentable = Rect(Int.MaxValue - 1, Int.MaxValue - 1, 10, 10)
    assert(unrepresentable.right < unrepresentable.x)
    assert(unrepresentable.bottom < unrepresentable.y)
    assert(!unrepresentable.contains(unrepresentable.x, unrepresentable.y))
    // inset still refuses to grow it, which is the property that has to hold for every rect and not just the sane ones
    assert(unrepresentable.inset(Int.MaxValue).isEmpty)
