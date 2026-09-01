package io.worxbend.tui.widgets

import org.scalatest.funsuite.AnyFunSuite

/** Pins the shared navigation rules: the clamping one that [[ListView]], [[DataTable]], [[Tree]] and [[DirectoryTree]]
  * delegate to, and the wrapping one [[Menu]] does.
  */
final class SelectionSpec extends AnyFunSuite:

  test("the first move in either direction lands on the first item") {
    assert(Selection.next(None, 3) == Some(0))
    assert(Selection.previous(None, 3) == Some(0))
    assert(Selection.moveWithin(Seq("a", "b", "c"), None, +1) == Some("a"))
    assert(Selection.moveWithin(Seq("a", "b", "c"), None, -1) == Some("a"))
  }

  test("moves clamp at the ends rather than wrapping round") {
    assert(Selection.next(Some(2), 3) == Some(2))
    assert(Selection.previous(Some(0), 3) == Some(0))
    assert(Selection.moveWithin(Seq("a", "b"), Some("b"), +1) == Some("b"))
    assert(Selection.moveWithin(Seq("a", "b"), Some("a"), -1) == Some("a"))
  }

  test("an empty list has nothing to select") {
    assert(Selection.next(Some(0), 0).isEmpty)
    assert(Selection.previous(Some(0), 0).isEmpty)
    assert(Selection.moveWithin(Seq.empty[String], Some("a"), +1).isEmpty)
  }

  test("a selection that has vanished from the list is treated as no selection") {
    assert(Selection.moveWithin(Seq("a", "b", "c"), Some("gone"), +1) == Some("a"))
  }

  test("first and last answer the ends of the list, and nothing on an empty one") {
    assert(Selection.first(3) == Some(0))
    assert(Selection.last(3) == Some(2))
    assert(Selection.first(0).isEmpty)
    assert(Selection.last(0).isEmpty)
  }

  test("a page-sized move clamps at both ends") {
    assert(Selection.by(Some(1), 10, +10) == Some(9))
    assert(Selection.by(Some(8), 10, -10) == Some(0))
    assert(Selection.by(Some(3), 10, +2) == Some(5))
    assert(Selection.by(Some(0), 0, +1).isEmpty)
  }

  test("a page-sized move with nothing selected starts from just outside the list") {
    // the same sentinel `moveWithin` uses, so a one-place move still lands on index 0 rather than skipping it
    assert(Selection.by(None, 10, +1) == Some(0))
    assert(Selection.by(None, 10, +10) == Some(9))
    assert(Selection.by(None, 10, -10) == Some(0))
  }

  test("next and previous are the one-place cases of the general move") {
    (0 until 4).foreach { count =>
      val positions = None +: (0 until count).map(Some(_))
      positions.foreach { current =>
        assert(Selection.next(current, count) == Selection.by(current, count, +1))
        assert(Selection.previous(current, count) == Selection.by(current, count, -1))
      }
    }
  }

  test("the popup rule wraps where the list rule clamps") {
    val anything = (_: Int) => true
    assert(Selection.nextSelectable(Some(2), 3, anything) == Some(0))
    assert(Selection.previousSelectable(Some(0), 3, anything) == Some(2))
  }

  test("the popup rule steps over entries it cannot land on") {
    val onlyEven = (index: Int) => index % 2 == 0
    assert(Selection.nextSelectable(Some(0), 4, onlyEven) == Some(2))
    assert(Selection.previousSelectable(Some(0), 4, onlyEven) == Some(2))
  }

  test("the popup rule gives up after one lap rather than spinning") {
    assert(Selection.nextSelectable(Some(0), 4, _ => false).isEmpty)
    assert(Selection.previousSelectable(None, 4, _ => false).isEmpty)
    assert(Selection.nextSelectable(None, 0, _ => true).isEmpty)
  }
