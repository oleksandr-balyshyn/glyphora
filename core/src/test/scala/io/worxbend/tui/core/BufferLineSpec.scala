package io.worxbend.tui.core

import org.scalatest.funsuite.AnyFunSuite

/** Drawing a [[Line]] and a [[Span]] into a [[Buffer]] — all three are `tui-core` values, and until these methods
  * existed the only code that could put one into another lived a module away, inside `tui-widgets`.
  */
final class BufferLineSpec extends AnyFunSuite:

  private def buffer(width: Int): Buffer = Buffer(Rect(0, 0, width, 1))

  private def row(buffer: Buffer): String =
    (0 until buffer.area.width).map(x => buffer.get(x, 0).symbol).mkString

  test("the spans of a line are written end to end, each keeping its own style"):
    val target  = buffer(6)
    val written = target.setLine(0, 0, Line(Seq(Span("ab", Style.Default.bold), Span("cd", Style.Default))), 6)
    assert(written == 4)
    assert(row(target).trim == "abcd")
    assert(target.get(0, 0).style.modifiers.hasAny(Modifiers.Bold))
    assert(!target.get(2, 0).style.modifiers.hasAny(Modifiers.Bold))

  test("a span's style is layered over the base style rather than replacing it"):
    // the base supplies the background the span says nothing about; the span keeps the foreground it does set
    val target = buffer(4)
    val _      =
      target.setLine(0, 0, Line(Seq(Span("x", Style.Default.withFg(Color.Red)))), 4, Style.Default.withBg(Color.Blue))
    assert(target.get(0, 0).style.fg.contains(Color.Red))
    assert(target.get(0, 0).style.bg.contains(Color.Blue))

  test("a line wider than its budget stops part way through the span that reaches the edge"):
    val target  = buffer(8)
    val written = target.setLine(0, 0, Line(Seq(Span("abc", Style.Default), Span("defg", Style.Default))), 5)
    assert(written == 5)
    assert(row(target) == "abcde   ")

  test("a wide cluster that would only half-fit inside the budget is dropped whole"):
    // "漢" is two columns; in a budget of three it would run one column past the edge, and half a wide glyph on a real
    // terminal is drawn across the column beyond the budget
    val target  = buffer(8)
    val written = target.setLine(0, 0, Line(Seq(Span("a漢", Style.Default))), 2)
    assert(written == 1)
    assert(row(target) == "a       ")

  test("a line whose budget is zero or negative writes nothing and reports nothing"):
    val target = buffer(4)
    assert(target.setLine(0, 0, Line(Seq(Span("abc", Style.Default))), 0) == 0)
    assert(target.setLine(0, 0, Line(Seq(Span("abc", Style.Default))), -3) == 0)
    assert(row(target) == "    ")

  test("an empty line writes nothing"):
    val target = buffer(4)
    assert(target.setLine(0, 0, Line(Seq.empty), 4) == 0)

  test("a line on a row outside the buffer writes nothing and reports nothing"):
    val target = buffer(4)
    assert(target.setLine(0, 5, Line(Seq(Span("abc", Style.Default))), 4) == 0)
    assert(row(target) == "    ")

  test("the budget is clipped by the buffer's own right edge as well"):
    val target  = buffer(3)
    val written = target.setLine(0, 0, Line(Seq(Span("abcdef", Style.Default))), 99)
    assert(written == 3)
    assert(row(target) == "abc")

  test("setSpan reports the columns a cluster occupies, not the characters it holds"):
    val target = buffer(6)
    // an emoji with a skin-tone modifier is several code points and one two-column cluster
    assert(target.setSpan(0, 0, Span("👍🏽", Style.Default), 6) == 2)
    assert(target.setSpan(2, 0, Span("é", Style.Default), 4) == 1)

  test("setLine lays wide clusters out on the columns they really occupy"):
    val target  = buffer(8)
    val written = target.setLine(0, 0, Line(Seq(Span("日本", Style.Default), Span("!", Style.Default))), 8)
    assert(written == 5)
    assert(target.get(0, 0).symbol == "日")
    assert(target.isContinuation(1, 0))
    assert(target.get(4, 0).symbol == "!")
