package io.worxbend.tui.terminal

import io.worxbend.tui.core.Rect

import org.scalatest.funsuite.AnyFunSuite

/** The rule that decides whether a resize has to erase the display before the frame goes out.
  *
  * The defect it exists for: drag a terminal narrower and the emulator reflows the rows it is already showing, wrapping
  * the overflow of each long row onto the row beneath. Those wrapped glyphs are outside the app's new, smaller area, so
  * repainting every cell the app owns leaves them there — a band of yesterday's border characters down the side of the
  * screen until something else happens to scroll them away.
  */
final class ScreenResetSpec extends AnyFunSuite:

  test("narrowing the display asks for an erase"):
    assert(ScreenReset.clearsOnShrink(Some(Rect(0, 0, 80, 24)), Rect(0, 0, 40, 24)))
    // one column narrower is still narrower: a single wrapped character is still a character the app cannot reach
    assert(ScreenReset.clearsOnShrink(Some(Rect(0, 0, 80, 24)), Rect(0, 0, 79, 24)))

  test("widening or keeping the same width does not"):
    assert(!ScreenReset.clearsOnShrink(Some(Rect(0, 0, 40, 24)), Rect(0, 0, 80, 24)))
    assert(!ScreenReset.clearsOnShrink(Some(Rect(0, 0, 80, 24)), Rect(0, 0, 80, 24)))

  test("a change in height alone does not"):
    // rows are not reflowed by a height change: the terminal either has room for them or drops them off the top,
    // and neither leaves a glyph inside the area the app is about to repaint
    assert(!ScreenReset.clearsOnShrink(Some(Rect(0, 0, 80, 50)), Rect(0, 0, 80, 24)))
    assert(!ScreenReset.clearsOnShrink(Some(Rect(0, 0, 80, 24)), Rect(0, 0, 80, 50)))

  test("shrinking in both directions asks for an erase, because the width is what reflows"):
    assert(ScreenReset.clearsOnShrink(Some(Rect(0, 0, 80, 50)), Rect(0, 0, 40, 24)))

  test("the first frame never erases, because there is nothing on screen to reflow"):
    // an unasked-for erase on the primary screen would destroy whatever the user was looking at before the app started
    assert(!ScreenReset.clearsOnShrink(None, Rect(0, 0, 1, 1)))
    assert(!ScreenReset.clearsOnShrink(None, Rect(0, 0, 200, 60)))

  test("a display that collapses to nothing is handled rather than special-cased"):
    // a zero-width terminal is reported by some emulators mid-drag; it is a shrink like any other and must not throw
    assert(ScreenReset.clearsOnShrink(Some(Rect(0, 0, 80, 24)), Rect(0, 0, 0, 0)))
    assert(!ScreenReset.clearsOnShrink(Some(Rect(0, 0, 0, 0)), Rect(0, 0, 0, 0)))

  test("the area's origin is irrelevant — only its width decides"):
    assert(ScreenReset.clearsOnShrink(Some(Rect(5, 5, 80, 24)), Rect(0, 0, 40, 24)))
    assert(!ScreenReset.clearsOnShrink(Some(Rect(0, 0, 40, 24)), Rect(5, 5, 40, 24)))
