package io.worxbend.tui.widgets

import io.worxbend.tui.core.Direction
import io.worxbend.tui.testsupport.BufferAssertions.{rendered, trimmedLines}

import org.scalatest.funsuite.AnyFunSuite

final class RuleSpec extends AnyFunSuite:

  test("a horizontal rule spans the width with an inline label"):
    assert(trimmedLines(rendered(Rule(), 6, 1)) == Seq("──────"))
    assert(trimmedLines(rendered(Rule(label = Some("cfg")), 10, 1)) == Seq("── cfg ───"))

  test("a vertical rule spans the height"):
    assert(trimmedLines(rendered(Rule(orientation = Direction.Vertical), 1, 3)) == Seq("│", "│", "│"))

  test("a rule's weight comes from the same border set a Block frames itself with"):
    assert(trimmedLines(rendered(Rule(borderType = BorderType.Double), 6, 1)) == Seq("══════"))
    assert(trimmedLines(rendered(Rule(borderType = BorderType.Thick), 6, 1)) == Seq("━━━━━━"))
    // rounding is a property of corners, of which a rule has none, so Rounded draws the plain run
    assert(trimmedLines(rendered(Rule(borderType = BorderType.Rounded), 6, 1)) == Seq("──────"))

  test("a vertical rule follows the same weight"):
    val double = rendered(Rule(orientation = Direction.Vertical, borderType = BorderType.Double), 1, 3)
    assert(trimmedLines(double) == Seq("║", "║", "║"))
    val thick  = rendered(Rule(orientation = Direction.Vertical, borderType = BorderType.Thick), 1, 3)
    assert(trimmedLines(thick) == Seq("┃", "┃", "┃"))

  test("a label still reads over a heavier run"):
    assert(trimmedLines(rendered(Rule(Some("cfg"), borderType = BorderType.Double), 10, 1)) == Seq("══ cfg ═══"))

  test("a label wider than the line is truncated, and a tiny rule draws no label at all"):
    // the caption is fitted to `width - 4`, so an 8-column rule shows four columns of " configuration "
    assert(trimmedLines(rendered(Rule(Some("configuration")), 8, 1)) == Seq("── con──"))
    assert(trimmedLines(rendered(Rule(Some("cfg")), 2, 1)) == Seq("──"))
    assert(trimmedLines(rendered(Rule(Some("cfg")), 0, 1)) == Seq(""))

  test("a two-column label is truncated on whole graphemes, never half a glyph"):
    // four columns of " 漢字 " hold the space and one wide glyph; the second would need two more, so it is dropped
    assert(trimmedLines(rendered(Rule(Some("漢字"), borderType = BorderType.Thick), 8, 1)) == Seq("━━ 漢━━━"))
    // three columns leave the same one glyph rather than half of the next — a torn half is never written
    assert(trimmedLines(rendered(Rule(Some("漢字")), 7, 1)) == Seq("── 漢──"))
