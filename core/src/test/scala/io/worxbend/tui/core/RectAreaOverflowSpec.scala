package io.worxbend.tui.core

import org.scalatest.funsuite.AnyFunSuite

/** What happens when a rectangle is so large that `width * height` no longer fits in an `Int`.
  *
  * 65536 * 65536 is 2^32, which wraps around to 0 in 32-bit arithmetic. No terminal is ever this big, but a `Rect` is
  * plain arithmetic over caller-supplied numbers, so a bug upstream can build one. Before this was checked, such a rect
  * allocated a zero-length buffer while `Rect.contains` went on answering true for coordinates "inside" it, and the
  * first write failed with an ArrayIndexOutOfBoundsException that named neither the rectangle nor its origin.
  */
final class RectAreaOverflowSpec extends AnyFunSuite:

  private val huge = Rect(0, 0, 65536, 65536)

  test("area wraps around for an oversized rect while cellCount stays exact"):
    assert(huge.area == 0)
    assert(huge.cellCount == 4294967296L)

  test("cellCount agrees with area for every rect small enough to render"):
    assert(Rect(1, 2, 3, 4).cellCount == 12L)
    assert(Rect(0, 0, -4, 4).cellCount == 0L)
    assert(Rect(0, 0, 200, 50).cellCount == Rect(0, 0, 200, 50).area.toLong)

  test("allocating a buffer for an oversized rect fails at construction, naming the rect"):
    val failure = intercept[IllegalArgumentException](Buffer(huge))
    assert(failure.getMessage.contains("65536"))
    assert(failure.getMessage.contains("4294967296"))

  test("an ordinary rect still builds a usable buffer"):
    val buffer = Buffer(Rect(0, 0, 200, 50))
    buffer.set(10, 10, Cell("x", Style.Default))
    assert(buffer.get(10, 10).symbol == "x")
