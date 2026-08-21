package io.worxbend.tui.widgets

import org.scalatest.funsuite.AnyFunSuite

/** Pins the shared navigation rule that [[ListView]], [[DataTable]], [[Tree]] and [[DirectoryTree]] all delegate to. */
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
